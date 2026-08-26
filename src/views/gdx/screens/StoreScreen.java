package views.gdx.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import models.shop.Currency;
import models.shop.DailyOffer;
import models.shop.Shop;
import models.shop.ShopItem;
import models.templates.PlantTemplate;
import models.user.Profile;
import models.user.User;
import utils.registry.PlantRegistry;
import views.gdx.core.GdxContext;
import views.gdx.ui.ConfirmDialog;
import views.gdx.ui.CurrencyHUD;
import views.gdx.ui.Cycler;
import views.gdx.ui.MenuStyles;
import views.gdx.ui.StoreCard;
import views.gdx.ui.UiArt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// The store.
//
// The listing is read off the Shop the AppSession already holds; buying goes through the same
// "shop buy -i <id> -n <n> [-t <plant>]" the terminal takes, so the price, the caps and every refusal
// remain the model's. What the screen adds is the two things a prompt cannot: a picker for the one item
// that needs a plant named, and a confirmation before the money goes.
//
// Descriptions live here rather than in the model because the model has none -- Shop names its items
// "pot", "random", "selective", "exchange", which is a key, not a sentence. Prices and quantities are
// NOT duplicated: they are read off ShopItem.
//
// ## Cards, not rows
//
// The first pass was a vertical list of six wide rows -- title, description, price, button -- which is
// how the terminal prints a shop and which read like a spreadsheet. Six products is a grid's worth, and
// the dump ships the real game's promoted-offer card art, so each item now stands on one (see
// StoreCard). The layout is fixed at three across and two down, sized so the whole stock is on screen
// without scrolling: a store where half the stock is below the fold is a worse read than a terse one.
public final class StoreScreen extends MenuScreen {

    private static final int ITEM_POT = 0;
    private static final int ITEM_PLANT_FOOD = 1;
    private static final int ITEM_RANDOM_PACKET = 2;
    private static final int ITEM_SELECTIVE_PACKET = 3;
    private static final int ITEM_EXCHANGE = 4;
    private static final int ITEM_DAILY = 5;

    // Three across, two down, and every card the same size. The height is what the panel can give once
    // its frame, title, header and Back button have taken theirs -- at 1280x720 there is no more.
    private static final int COLUMNS = 3;
    private static final float CARD_WIDTH = 296f;
    private static final float CARD_HEIGHT = 250f;
    private static final float CARD_PAD = 6f;
    private static final float LIST_WIDTH = 940f;
    private static final float LIST_HEIGHT = 528f;

    private static final Color COIN_TEXT = new Color(1f, 0.88f, 0.45f, 1f);
    private static final Color GEM_TEXT = new Color(0.55f, 0.82f, 1f, 1f);
    private static final Color DIM = new Color(0.78f, 0.76f, 0.72f, 1f);
    // The countdown. Warm, so it reads as a clock running down rather than as another price.
    private static final Color TIMER_TEXT = new Color(1f, 0.72f, 0.32f, 1f);

    private static final String ICON_COINS = "image_ui_coins_stack_0";
    private static final String ICON_GEMS = "image_ui_gems_stack_1";

    // One product image per item, all of them shipped art confirmed with -Dpvz.probeRegions. The
    // chosen-packet card is the exception: its icon is the seed packet of whichever plant the picker is
    // on, so it changes as the player cycles.
    private static final String ART_POT = "IMAGE_ZEN_GARDEN_GROWING_PLANT_SLOT_GROWING_PLANT_SLOT_184X161";
    // The same jar PickupFlights flies to the plant-food counter, so a bought one and a looted one are
    // the same object to the player.
    private static final String ART_PLANT_FOOD = "IMAGE_UI_ALMANAC_ALMANAC_STAT_ICON_PLANTFOOD_LARGE";
    private static final String ART_PACKETS = "IMAGE_UI_STOREMULTI_SEEDPACKETICON";
    private static final String ART_EXCHANGE = "IMAGE_UI_STORE_OFFERWALL_TREASURE_CHEST_COINS";
    // The boosted seed packet, from the same atlas as the plain ones. The obvious pick was the piñata
    // prize-reveal art, which resolves and is 98x98 -- and draws as a flat red square, because it is one
    // frame of a flash effect rather than a picture of a piñata. Probing an id says it exists, not that
    // it is a picture of what its name suggests.
    private static final String ART_DEAL = "IMAGE_UI_QUESTS_EPIC_REWARD_PINATA";

    // How many of an item may be bought in one go. The model caps several of them for its own reasons
    // (three plant food, a finite greenhouse), and reports when it does; this is only what the stepper
    // offers. Written "x3" rather than "3" because on a card the number stands alone with no column
    // heading to say what it counts.
    private static final String[] QUANTITIES = {"x1", "x2", "x3", "x4", "x5"};

    private Table grid;
    private final CurrencyHUD wallet;
    private UiArt art;

    // The plant the selective seed packet will buy for. Chosen from what the player already owns,
    // because the model refuses a locked one.
    private List<String> ownedPlants = List.of();
    private int plantIndex;

    // How many of each item the player has dialled up, keyed by item id.
    //
    // One shared int here was wrong twice over: a stepper moved on ANY row set the count for EVERY row,
    // and because rebuild() re-seeds each Cycler from the field after every purchase, one trip to "2" on
    // the mystery packets left the whole store stuck at 2 -- so the next thing bought, whatever it was,
    // cost double with nobody having asked for two of it.
    private final Map<Integer, Integer> quantities = new HashMap<>();

    // The deal's countdown, and the last thing written to it. Re-read every frame; only rewritten when
    // the minute rolls over, because setText on a Label invalidates the layout of everything above it.
    private Label deadline;
    private String deadlineText = "";

    // What each card costs, so the grid can grey out whatever the player cannot afford yet. Rebuilt
    // whenever the grid is.
    private final List<Priced> priced = new ArrayList<>();

    private record Priced(StoreCard card, int cost, Currency currency, boolean soldOut) { }

    public StoreScreen(GdxContext context) {
        super(context);
        wallet = new CurrencyHUD(skin, debugCheat("coin"), debugCheat("gem"));
    }

    // Lays its own out, in the header beside the prices it explains.
    @Override
    protected boolean showsOwnCurrency() {
        return true;
    }

    @Override
    protected void build(Table root) {
        art = new UiArt(context.assets());
        Table panel = MenuPanel.build(skin, "Store");

        Table head = new Table();
        head.add(subtitle("Everything a lawn needs, and a few things it does not.")).left();
        head.add().expandX();
        head.add(wallet).right();
        panel.add(head).width(LIST_WIDTH).padBottom(8f).row();

        grid = new Table();
        grid.top();
        ScrollPane pane = new ScrollPane(grid, skin);
        pane.setScrollingDisabled(true, false);
        pane.setFadeScrollBars(false);
        pane.setOverscroll(false, false);
        panel.add(pane).width(LIST_WIDTH).height(LIST_HEIGHT).padBottom(10f).row();

        TextButton back = MenuStyles.button(skin, "Back", MenuStyles.BUTTON_PURPLE);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                goBack();
            }
        });
        panel.add(back).width(200f).height(50f).row();

        root.setFillParent(true);
        root.add(panel);

        rebuild();
    }

    private void rebuild() {
        grid.clearChildren();
        priced.clear();
        deadline = null;
        Shop shop = context.appSession().getShop();
        if (shop == null) {
            grid.add(MenuStyles.label(skin, "The shop is closed. Come back later!", MenuStyles.TEXT))
                    .pad(40f);
            return;
        }
        ownedPlants = ownedPlants();
        place(dailyCard(shop.getDailyOffer()), 0);
        int slot = 1;
        for (ShopItem item : shop.getPermanentItems()) {
            place(itemCard(item), slot++);
        }
    }

    // Wraps every COLUMNS cards onto a new line. The row break comes BEFORE the card rather than after
    // the third one, so a trailing empty row is impossible however many products the shop grows to.
    private void place(StoreCard card, int slot) {
        if (slot > 0 && slot % COLUMNS == 0) {
            grid.row();
        }
        grid.add(card).size(CARD_WIDTH, CARD_HEIGHT).pad(CARD_PAD);
    }

    // Today's deal. It is not a ShopItem -- DailyOffer carries its own base and discount price -- so it
    // gets its own card rather than being forced into the same shape.
    //
    // Whether it has been taken is asked of the PROFILE, not of the offer. The offer is the same for
    // everyone and is rebuilt with the AppSession; the purchase is the player's and is in the save file.
    private StoreCard dailyCard(DailyOffer offer) {
        StoreCard card = new StoreCard(skin, art, CARD_WIDTH, CARD_HEIGHT);
        card.title("Today's Deal").icon(region(ART_DEAL));
        card.price(dealPrice(offer), null);
        // What the deal actually IS. The card said "Today's Deal", showed a piñata and counted down to
        // midnight, and nowhere said what the 1600 coins bought -- the countdown had taken the line every
        // other card uses for its description.
        card.note(offer.getPackets() + " packets for a plant you grow.");
        Profile profile = profile();
        if (profile != null && profile.isHasBoughtDailyOfferToday()) {
            card.control(subtitle("Bought. A fresh one tomorrow."));
            card.action(sold("Sold out"));
            card.exhaust();
            priced.add(new Priced(card, offer.getDiscountPrice(), Currency.COIN, true));
            return card;
        }
        // The countdown moves to the control slot, which is empty on this card -- the two lines are
        // different things and the deal is the one worth reading first.
        deadline = MenuStyles.label(skin, countdown(), MenuStyles.TEXT);
        deadline.setColor(TIMER_TEXT);
        deadline.setFontScale(0.72f);
        deadline.setAlignment(Align.center);
        deadlineText = deadline.getText().toString();
        card.control(deadline);
        card.action(buy("Buy Deal", () -> "shop buy -i " + ITEM_DAILY + " -n 1",
                () -> "Buy today's deal for " + offer.getDiscountPrice() + " coins?"));
        card.promote();
        priced.add(new Priced(card, offer.getDiscountPrice(), Currency.COIN, false));
        return card;
    }

    private StoreCard itemCard(ShopItem item) {
        StoreCard card = new StoreCard(skin, art, CARD_WIDTH, CARD_HEIGHT);
        card.title(title(item)).icon(iconFor(item));
        if (item.getId() == ITEM_SELECTIVE_PACKET) {
            // The picker takes the description's line rather than adding one of its own: it is the only
            // card carrying both a picker and a stepper, and a sixth row would have squeezed the product
            // image down to a smudge.
            card.control(plantPicker(card, item));
        } else {
            card.note(description(item));
        }
        card.price(priceTag(item.getPrice(), item.getCurrency()), stepper(item));
        boolean buyable = item.getId() != ITEM_SELECTIVE_PACKET || !ownedPlants.isEmpty();
        card.action(buyable
                ? buy("Buy", () -> command(item), () -> confirmation(item))
                : sold("Unlock a plant first"));
        priced.add(new Priced(card, item.getPrice(), item.getCurrency(), !buyable));
        return card;
    }

    // The plant this card will buy packets for. Changing it swaps the card's product image too, since
    // the game ships a finished seed packet for every plant and that is a far better picture of what is
    // being bought than a generic stack.
    private Actor plantPicker(StoreCard card, ShopItem item) {
        if (ownedPlants.isEmpty()) {
            return subtitle(item.getCapacity() + " packets, plant of your choosing.");
        }
        Cycler picker = new Cycler(skin, ownedPlants.toArray(new String[0]), plantIndex);
        picker.compact(126f, 22f, 0.72f);
        picker.onChange(index -> {
            plantIndex = index;
            card.icon(packetFor(selectedPlant()));
        });
        card.icon(packetFor(selectedPlant()));
        return picker;
    }

    // How many to buy. The pot is sold one at a time, so it has no stepper and can have no dialled-up
    // count either -- see quantityOf.
    private Actor stepper(ShopItem item) {
        if (item.getCapacity() <= 0 || item.getId() == ITEM_POT) {
            return null;
        }
        Cycler amount = new Cycler(skin, QUANTITIES, quantityOf(item) - 1);
        amount.compact(42f, 22f, 0.8f);
        amount.onChange(index -> quantities.put(item.getId(), index + 1));
        return amount;
    }

    // The pot is sold one at a time: it has no stepper, so it can have no dialled-up count.
    private int quantityOf(ShopItem item) {
        if (item.getId() == ITEM_POT) {
            return 1;
        }
        return quantities.getOrDefault(item.getId(), 1);
    }

    // The command string, built exactly as the terminal's grammar wants it. -t is only sent for the
    // selective packet; the other items ignore it.
    private String command(ShopItem item) {
        String base = "shop buy -i " + item.getId() + " -n " + quantityOf(item);
        if (item.getId() != ITEM_SELECTIVE_PACKET) {
            return base;
        }
        return base + " -t " + selectedPlant();
    }

    private String selectedPlant() {
        if (ownedPlants.isEmpty()) {
            return "";
        }
        return ownedPlants.get(Math.min(plantIndex, ownedPlants.size() - 1));
    }

    private String confirmation(ShopItem item) {
        int count = quantityOf(item);
        String money = (item.getPrice() * count) + " "
                + (item.getCurrency() == Currency.COIN ? "coins" : "gems");
        if (item.getId() == ITEM_SELECTIVE_PACKET) {
            return "Buy " + (count * item.getCapacity()) + " seed packets for "
                    + selectedPlant() + " for " + money + "?";
        }
        return "Buy " + count + " x " + title(item) + " for " + money + "?";
    }

    // Every purchase is confirmed before it is posted. Nothing else on this screen spends anything, so
    // there is no case where the dialog is in the way of a harmless action.
    //
    // The press feedback -- sinking to 0.95 and springing back past 1 on release -- is not wired here.
    // Every button in the game gets it from MenuStyles.button, which attaches ButtonJuice; doing it per
    // screen is how a UI ends up with six slightly different ones.
    private TextButton buy(String label, Supplier<String> command, Supplier<String> question) {
        TextButton button = MenuStyles.button(skin, label, MenuStyles.BUTTON_GREEN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                // Read once, here, so the sentence the player agrees to and the command that is posted
                // are the same purchase. Asking the supplier again inside the callback would let a
                // stepper moved while the dialog is open change the price after it was confirmed.
                String posted = command.get();
                ConfirmDialog.show(stage, context.assets(), skin, "Confirm Purchase", question.get(),
                        "Buy it", () -> {
                            commands.submit(posted);
                            rebuild();
                        });
            }
        });
        return button;
    }

    // Where a Buy button would be, on a card that has nothing to sell right now.
    private Table sold(String reason) {
        Table box = new Table();
        box.add(subtitle(reason)).center();
        return box;
    }

    // ---- the daily countdown ------------------------------------------------------------------
    //
    // The deal runs until the day rolls over, and the day that rolls is the model's own: Profile
    // .ensureQuestDay stamps java.time.LocalDate.now() and clears hasBoughtDailyOfferToday the first
    // time it sees a different one. So "when does this end" is midnight local, and reading the same
    // clock here is what keeps the number on the card honest rather than a second, parallel definition
    // of a day.

    private static String countdown() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.Duration left = java.time.Duration.between(
                now, now.toLocalDate().plusDays(1).atStartOfDay());
        long hours = left.toHours();
        if (hours > 0) {
            return "Ends in: " + hours + "h " + left.toMinutesPart() + "m";
        }
        // The last hour counts seconds. "Ends in: 0h 3m" is the moment a countdown stops being
        // information and starts being decoration.
        return "Ends in: " + left.toMinutesPart() + "m " + left.toSecondsPart() + "s";
    }

    // ---- item text ----------------------------------------------------------------------------
    //
    // Shop stores keys ("pot", "random", "selective", "exchange"), not sentences, so the readable half
    // of each listing lives here. Quantities inside the sentences are read off the item rather than
    // written out, so a change to Shop's capacities does not leave this lying.

    // Short, because the card's title sits on a banner exactly one line tall. "Mystery Seed Packets"
    // wrapped, and the second line was drawn off the top of the card and across the one above it.
    private String title(ShopItem item) {
        return switch (item.getId()) {
            case ITEM_POT -> "Greenhouse Pot";
            case ITEM_PLANT_FOOD -> "Plant Food";
            case ITEM_RANDOM_PACKET -> "Mystery Packets";
            case ITEM_SELECTIVE_PACKET -> "Chosen Packets";
            case ITEM_EXCHANGE -> "Gem Exchange";
            default -> item.getName();
        };
    }

    // One short line each. A card is 296 wide, not 480, so anything that ran to two lines on the old
    // listing has to lose its second half here -- the space it would take comes straight out of the
    // product image.
    private String description(ShopItem item) {
        return switch (item.getId()) {
            case ITEM_POT -> "One more pot in the greenhouse.";
            case ITEM_PLANT_FOOD -> "Hold three jars at a time.";
            case ITEM_RANDOM_PACKET -> item.getCapacity() + " packets, plant picked for you.";
            case ITEM_SELECTIVE_PACKET -> item.getCapacity() + " packets, plant of your choosing.";
            case ITEM_EXCHANGE -> item.getPrice() + " gems become " + item.getCapacity() + " coins.";
            default -> "";
        };
    }

    private TextureRegion iconFor(ShopItem item) {
        return switch (item.getId()) {
            case ITEM_POT -> region(ART_POT);
            case ITEM_PLANT_FOOD -> region(ART_PLANT_FOOD);
            case ITEM_EXCHANGE -> region(ART_EXCHANGE);
            case ITEM_SELECTIVE_PACKET -> packetFor(selectedPlant());
            default -> region(ART_PACKETS);
        };
    }

    // The plant's own finished seed packet, or the blank one it would have been printed on.
    private TextureRegion packetFor(String plant) {
        TextureRegion packet = art.packet(plant);
        return packet != null ? packet : region(UiArt.SEED_PACKET);
    }

    // ---- small parts --------------------------------------------------------------------------

    // The price, with the coin or gem it is quoted in. Two currencies on one screen is exactly the
    // situation where a bare number gets misread.
    private Table priceTag(int amount, Currency currency) {
        boolean coin = currency == Currency.COIN;
        Label value = MenuStyles.label(skin, String.valueOf(amount), MenuStyles.TEXT);
        value.setColor(coin ? COIN_TEXT : GEM_TEXT);

        Table tag = new Table();
        tag.add(icon(coin ? ICON_COINS : ICON_GEMS)).size(24f).padRight(6f);
        tag.add(value);
        return tag;
    }

    // The deal's price: what it was, struck out, then what it is.
    //
    // Dimming and shrinking the old number was not enough on its own -- read quickly, "2000  [coin]1600"
    // is two numbers, and the first one is where the eye lands. It is now actually struck through, which
    // is the only presentation of a former price that needs no explaining.
    private Table dealPrice(DailyOffer offer) {
        Label was = MenuStyles.label(skin, String.valueOf(offer.getBasePrice()), MenuStyles.TEXT);
        was.setColor(DIM);
        was.setFontScale(0.7f);

        Table tag = new Table();
        tag.add(struck(was)).padRight(10f);
        tag.add(priceTag(offer.getDiscountPrice(), Currency.COIN));
        return tag;
    }

    // A rule drawn across a label, from the skin's own white pixel.
    //
    // The font ships no strike-through face, so this is a Stack: the label underneath, and over it a
    // Table holding a two-pixel fill that grows to the label's width. A Table centres its contents by
    // default, which is what puts the rule through the middle of the digits rather than under them.
    private Actor struck(Label label) {
        Table rule = new Table();
        rule.add(new Image(context.assets().solid(DIM))).height(2f).growX();

        Stack stack = new Stack();
        stack.add(label);
        stack.add(rule);
        return stack;
    }

    private Actor icon(String id) {
        Drawable drawable = MenuStyles.drawable(skin, id);
        return drawable == null ? new Table() : new Image(drawable);
    }

    private Label subtitle(String text) {
        Label label = MenuStyles.label(skin, text, MenuStyles.TEXT);
        label.setColor(DIM);
        label.setFontScale(0.8f);
        label.setAlignment(Align.center);
        return label;
    }

    // What the selective packet may be bought for. The model refuses a locked plant, so offering one
    // would only produce a refusal; the picker shows what will actually work.
    //
    // Names come back through the registry because the Profile stores them lower-cased -- "cabbage-pult"
    // in a picker beside "Cabbage-pult" everywhere else looks like a different plant. Which spelling is
    // submitted does not matter: Profile.addSeedPackets lower-cases its own key, and the shop's ownership
    // check compares ignoring case.
    private List<String> ownedPlants() {
        Profile profile = profile();
        if (profile == null || profile.getUnlockedPlants() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String owned : profile.getUnlockedPlants()) {
            PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(owned);
            names.add(template == null ? owned : template.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    // Called every frame by MenuScreen.render, immediately before stage.act. The two live parts of the
    // screen are the countdown and which cards the player can currently afford.
    @Override
    protected void refresh() {
        wallet.refresh(profile());
        tickDeadline();
        tickAffordability();
    }

    private void tickDeadline() {
        if (deadline == null) {
            return;
        }
        String now = countdown();
        // Only when it actually changes. setText invalidates the label's layout and everything above it
        // in the card, and for fifty-nine of every sixty frames the minute has not moved.
        if (!now.equals(deadlineText)) {
            deadlineText = now;
            deadline.setText(now);
        }
    }

    // A card the player cannot pay for is faded, so what is within reach reads at a glance. It is still
    // pressable: the model's refusal names the currency that is short, which is more use than a dead
    // button.
    private void tickAffordability() {
        Profile profile = profile();
        for (Priced entry : priced) {
            if (entry.soldOut()) {
                continue;
            }
            boolean afford = profile != null && (entry.currency() == Currency.COIN
                    ? profile.getCoins() >= entry.cost()
                    : profile.getGems() >= entry.cost());
            entry.card().getColor().a = afford ? 1f : 0.62f;
        }
    }

    private Profile profile() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getProfile();
    }

}
