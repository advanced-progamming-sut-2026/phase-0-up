package views.gdx.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
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
import views.gdx.ui.Cycler;
import views.gdx.ui.MenuStyles;

import java.util.ArrayList;
import java.util.List;

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
public final class StoreScreen extends MenuScreen {

    private static final int ITEM_POT = 0;
    private static final int ITEM_PLANT_FOOD = 1;
    private static final int ITEM_RANDOM_PACKET = 2;
    private static final int ITEM_SELECTIVE_PACKET = 3;
    private static final int ITEM_EXCHANGE = 4;
    private static final int ITEM_DAILY = 5;

    private static final float LIST_WIDTH = 960f;
    private static final float LIST_HEIGHT = 515f;
    private static final float ROW_PAD = 10f;
    // The three columns of a listing row. They add up to the row's inner width exactly; leaving slack
    // lets the description column take it and re-wrap differently from row to row.
    private static final float TEXT_WIDTH = 480f;
    private static final float PRICE_WIDTH = 170f;
    private static final float CONTROL_WIDTH = 250f;

    private static final Color COIN_TEXT = new Color(1f, 0.88f, 0.45f, 1f);
    private static final Color GEM_TEXT = new Color(0.55f, 0.82f, 1f, 1f);
    private static final Color DIM = new Color(0.78f, 0.76f, 0.72f, 1f);
    private static final Color ROW_FACE = new Color(0f, 0f, 0f, 0.32f);
    private static final Color DEAL_FACE = new Color(0.22f, 0.16f, 0.05f, 0.62f);

    private static final String ICON_COINS = "image_ui_coins_stack_0";
    private static final String ICON_GEMS = "image_ui_gems_stack_1";

    // How many of an item may be bought in one go. The model caps several of them for its own reasons
    // (three plant food, a finite greenhouse), and reports when it does; this is only what the stepper
    // offers.
    private static final String[] QUANTITIES = {"1", "2", "3", "4", "5"};

    private Table list;
    private Label coins;
    private Label gems;

    // The plant the selective seed packet will buy for. Chosen from what the player already owns,
    // because the model refuses a locked one.
    private List<String> ownedPlants = List.of();
    private int plantIndex;
    private int quantity = 1;

    public StoreScreen(GdxContext context) {
        super(context);
    }

    @Override
    protected void build(Table root) {
        Table panel = MenuPanel.build(skin, "Store");

        Table head = new Table();
        head.add(subtitle("Everything a lawn needs, and a few things it does not.")).left();
        head.add().expandX();
        head.add(wallet()).right();
        panel.add(head).width(LIST_WIDTH).padBottom(10f).row();

        list = new Table();
        list.top();
        ScrollPane pane = new ScrollPane(list, skin);
        pane.setScrollingDisabled(true, false);
        pane.setFadeScrollBars(false);
        pane.setOverscroll(false, false);
        panel.add(pane).width(LIST_WIDTH).maxHeight(LIST_HEIGHT).padBottom(14f).row();

        TextButton back = MenuStyles.button(skin, "Back", MenuStyles.BUTTON_PURPLE);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                goBack();
            }
        });
        panel.add(back).width(200f).height(54f).row();

        root.setFillParent(true);
        root.add(panel);

        rebuild();
    }

    private Table wallet() {
        coins = MenuStyles.label(skin, "0", MenuStyles.TEXT);
        coins.setColor(COIN_TEXT);
        gems = MenuStyles.label(skin, "0", MenuStyles.TEXT);
        gems.setColor(GEM_TEXT);

        Table row = new Table();
        row.add(icon(ICON_COINS)).size(30f).padRight(6f);
        row.add(coins).minWidth(80f).left().padRight(18f);
        row.add(icon(ICON_GEMS)).size(30f).padRight(6f);
        row.add(gems).minWidth(70f).left();
        return row;
    }

    private void rebuild() {
        list.clearChildren();
        Shop shop = context.appSession().getShop();
        if (shop == null) {
            list.add(MenuStyles.label(skin, "The shop is closed. Come back later!", MenuStyles.TEXT))
                    .pad(40f);
            return;
        }
        ownedPlants = ownedPlants();
        list.add(dailyRow(shop.getDailyOffer())).width(LIST_WIDTH - 24f).padBottom(ROW_PAD).row();
        for (ShopItem item : shop.getPermanentItems()) {
            list.add(itemRow(item)).width(LIST_WIDTH - 24f).padBottom(ROW_PAD).row();
        }
    }

    // Today's deal. It is not a ShopItem -- DailyOffer carries its own base and discount price and a
    // purchased flag -- so it gets its own row rather than being forced into the same shape.
    private Table dailyRow(DailyOffer offer) {
        Table row = shell(DEAL_FACE);
        row.add(text("Today's Deal", "Ten packets for a plant you grow. One per day."))
                .width(TEXT_WIDTH).left();

        Table price = new Table();
        Label was = MenuStyles.label(skin, String.valueOf(offer.getBasePrice()), MenuStyles.TEXT);
        was.setColor(DIM);
        was.setFontScale(0.75f);
        price.add(was).row();
        price.add(priceTag(offer.getDiscountPrice(), Currency.COIN)).row();
        row.add(price).width(PRICE_WIDTH);

        if (offer.isPurchased()) {
            row.add(subtitle("Bought today")).width(CONTROL_WIDTH);
            return row;
        }
        row.add(buy("Buy Deal", "shop buy -i " + ITEM_DAILY + " -n 1",
                "Buy today's deal for " + offer.getDiscountPrice() + " coins?")).width(CONTROL_WIDTH);
        return row;
    }

    private Table itemRow(ShopItem item) {
        Table row = shell(ROW_FACE);
        row.add(text(title(item), description(item))).width(TEXT_WIDTH).left();
        row.add(priceTag(item.getPrice(), item.getCurrency())).width(PRICE_WIDTH);
        row.add(controls(item)).width(CONTROL_WIDTH);
        return row;
    }

    // The right-hand third of a row: a quantity stepper, a plant picker where one is needed, and the
    // button.
    private Table controls(ShopItem item) {
        Table box = new Table();
        if (item.getId() == ITEM_SELECTIVE_PACKET) {
            if (ownedPlants.isEmpty()) {
                box.add(subtitle("Unlock a plant first."));
                return box;
            }
            Cycler picker = new Cycler(skin, ownedPlants.toArray(new String[0]), plantIndex);
            picker.onChange(index -> plantIndex = index);
            box.add(picker).padBottom(6f).row();
        }
        if (item.getCapacity() > 0 && item.getId() != ITEM_POT) {
            Cycler amount = new Cycler(skin, QUANTITIES, quantity - 1);
            amount.onChange(index -> quantity = index + 1);
            box.add(amount).padBottom(6f).row();
        }
        box.add(buy("Buy", command(item), confirmation(item))).width(200f).height(50f);
        return box;
    }

    // The command string, built exactly as the terminal's grammar wants it. -t is only sent for the
    // selective packet; the other items ignore it.
    private String command(ShopItem item) {
        int count = item.getId() == ITEM_POT ? 1 : quantity;
        String base = "shop buy -i " + item.getId() + " -n " + count;
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
        int count = item.getId() == ITEM_POT ? 1 : quantity;
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
    private TextButton buy(String label, String command, String question) {
        TextButton button = MenuStyles.button(skin, label, MenuStyles.BUTTON_GREEN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                ConfirmDialog.show(stage, context.assets(), skin, "Confirm Purchase", question,
                        "Buy it", () -> {
                            commands.submit(command);
                            rebuild();
                        });
            }
        });
        return button;
    }

    // ---- item text ----------------------------------------------------------------------------
    //
    // Shop stores keys ("pot", "random", "selective", "exchange"), not sentences, so the readable half
    // of each listing lives here. Quantities inside the sentences are read off the item rather than
    // written out, so a change to Shop's capacities does not leave this lying.

    private String title(ShopItem item) {
        return switch (item.getId()) {
            case ITEM_POT -> "Greenhouse Pot";
            case ITEM_PLANT_FOOD -> "Plant Food";
            case ITEM_RANDOM_PACKET -> "Mystery Seed Packets";
            case ITEM_SELECTIVE_PACKET -> "Chosen Seed Packets";
            case ITEM_EXCHANGE -> "Gem Exchange";
            default -> item.getName();
        };
    }

    // One line each, at TEXT_WIDTH. Two-line descriptions push the six listings past the height the
    // panel has, and a store where half the stock is below the fold is a worse read than a terse one.
    private String description(ShopItem item) {
        return switch (item.getId()) {
            case ITEM_POT -> "One more pot in the greenhouse.";
            case ITEM_PLANT_FOOD -> "One jar. You can hold three at a time.";
            case ITEM_RANDOM_PACKET -> item.getCapacity() + " packets, plant picked for you.";
            case ITEM_SELECTIVE_PACKET -> item.getCapacity() + " packets, plant of your choosing.";
            case ITEM_EXCHANGE -> item.getPrice() + " gems become " + item.getCapacity() + " coins.";
            default -> "";
        };
    }

    // ---- small parts --------------------------------------------------------------------------

    private Table shell(Color face) {
        Table row = new Table();
        row.setBackground(context.assets().solid(face));
        row.pad(10f, 14f, 10f, 14f);
        return row;
    }

    private Table text(String heading, String body) {
        Table box = new Table();
        Label title = MenuStyles.label(skin, heading, MenuStyles.HEADING);
        title.setAlignment(Align.left);
        box.add(title).growX().left().row();

        Label detail = MenuStyles.label(skin, body, MenuStyles.TEXT);
        detail.setWrap(true);
        detail.setAlignment(Align.left);
        detail.setColor(DIM);
        detail.setFontScale(0.8f);
        box.add(detail).growX().left().padTop(2f).row();
        return box;
    }

    // The price, with the coin or gem it is quoted in. Two currencies on one screen is exactly the
    // situation where a bare number gets misread.
    private Table priceTag(int amount, Currency currency) {
        boolean coin = currency == Currency.COIN;
        Label value = MenuStyles.label(skin, String.valueOf(amount), MenuStyles.TEXT);
        value.setColor(coin ? COIN_TEXT : GEM_TEXT);

        Table tag = new Table();
        tag.add(icon(coin ? ICON_COINS : ICON_GEMS)).size(28f).padRight(6f);
        tag.add(value);
        return tag;
    }

    private Actor icon(String id) {
        Drawable art = MenuStyles.drawable(skin, id);
        return art == null ? new Table() : new Image(art);
    }

    private Label subtitle(String text) {
        Label label = MenuStyles.label(skin, text, MenuStyles.TEXT);
        label.setColor(DIM);
        label.setFontScale(0.8f);
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

    @Override
    protected void refresh() {
        Profile profile = profile();
        coins.setText(profile == null ? "0" : String.valueOf(profile.getCoins()));
        gems.setText(profile == null ? "0" : String.valueOf(profile.getGems()));
    }

    private Profile profile() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getProfile();
    }

    // Two exits, not one.
    //
    // The model's route out of the shop is the greenhouse (ExitMenuCommand: SHOP -> GREENHOUSE), and
    // the greenhouse has no screen until Phase 6 -- so a single exit would land the session in a menu
    // ScreenManager cannot draw, leaving the player looking at the store with a dead Back button. Both
    // hops are real commands and both are legal; the second one comes out the day T6.1 lands.
    @Override
    protected void goBack() {
        commands.back();
        commands.back();
    }
}
