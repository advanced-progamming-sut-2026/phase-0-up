package views.gdx.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import models.greenhouse.GreenHouse;
import models.greenhouse.GreenHousePlant;
import models.greenhouse.Pot;
import models.shop.ShopItem;
import models.templates.PlantTemplate;
import models.user.Profile;
import models.user.User;
import utils.registry.PlantRegistry;
import views.gdx.core.DebugFlags;
import views.gdx.core.GdxContext;
import views.gdx.core.PvZGame;
import views.gdx.sprite.EntitySprite;
import views.gdx.ui.ConfirmDialog;
import views.gdx.ui.MenuStyles;
import views.gdx.ui.PotTile;
import views.gdx.ui.WalletBar;

import java.util.ArrayList;
import java.util.List;

// The greenhouse: twelve pots, standing on the twelve slat mats the background is painted with.
//
// **The background IS the screen.** There is no dialog frame here, unlike every other menu -- the spec
// asks for "a specific Greenhouse background ... with a pot placed on each slot", and a panel over the
// top would hide the very artwork the pots have to line up with. The title, the wallet and the two
// buttons float directly on the art in the skin's outlined faces, which exist for exactly this.
//
// **Pot positions come from the painting, not from a layout.** IMAGE_BACKGROUNDS_ZEN_GARDEN is 1750x774
// and its mats sit in perspective, so their spacing is NOT uniform -- the rows are further apart towards
// the front. Each mat was measured in the background's own pixel space (see SLOT_X / SLOT_FLOOR_Y) and
// every tile is placed by pushing that measurement through the SAME scale-and-offset used to draw the
// background. Alignment is therefore correct by construction: change how the background is fitted and the
// pots follow it. A Table would distribute the twelve evenly and drift off the art immediately.
//
// **The transposed accessor.** GreenHouse.getPot(x, y) is pots[y][x] -- x is the COLUMN, y the ROW -- and
// is 0-based, as is isValidCoordinate. The player types 1-based coordinates, so every tile carries its
// own 1-based pair and the commands convert once.
public final class GreenhouseScreen extends MenuScreen {

    // The real game's Zen Garden interior: a greenhouse with twelve slat mats in three rows of four.
    private static final String BACKDROP = "IMAGE_BACKGROUNDS_ZEN_GARDEN";
    // The natural size of that image at the 768 resolution the game loads. Every measurement below is in
    // this space.
    private static final float BG_WIDTH = 1750f;
    private static final float BG_HEIGHT = 774f;

    // Mat centres and the line each pot's base rests on, measured off the painting. Not a formula: the
    // mats recede, so the row pitch grows towards the front (166 then 168) and the columns are not quite
    // even either. y is measured DOWN from the top of the image, as image coordinates are.
    private static final float[] SLOT_X = {622.5f, 793.75f, 960.6f, 1128.75f};
    private static final float[] SLOT_FLOOR_Y = {368f, 534f, 702f};

    // A tile's box, in the same background pixels, so it scales with the art. Wide enough for the pot
    // plus its buttons, and just under the 171px column pitch so neighbours do not overlap.
    private static final float SLOT_WIDTH = 162f;
    private static final float SLOT_HEIGHT = 168f;

    // The Zen Garden's own pot, gold ripe pot, padlock, timer banner and gem.
    //
    // GROWING_PLANT_SLOT holds four sub-images under one name -- terracotta pot, gold pot, shield badge,
    // and a bare black ellipse that is the pot's DROP SHADOW. They are told apart only by pixel size.
    private static final String POT_ART = "IMAGE_ZEN_GARDEN_GROWING_PLANT_SLOT_GROWING_PLANT_SLOT_184X161";
    private static final String POT_RIPE_ART =
            "IMAGE_ZEN_GARDEN_GROWING_PLANT_SLOT_GROWING_PLANT_SLOT_184X161_2";
    private static final String LOCK_ART = "IMAGE_ZEN_GARDEN_LOCKED_POT_ICON";
    private static final String BANNER_ART = "IMAGE_ZEN_GARDEN_FINISH_TIMER_BACKGROUND";
    private static final String GEM_ART = "IMAGE_ZEN_GARDEN_GEM_LARGE";


    // The shop's id for a greenhouse pot. Pots are sold nowhere else, which is why the Buy button has to
    // go through the shop -- see buyPot.
    private static final int SHOP_ITEM_POT = 0;

    private static final Color DIM = new Color(0.90f, 0.88f, 0.84f, 1f);
    // Behind the title and the button bar only. The greenhouse art is bright -- pale mats, sunlit glass --
    // and outlined text alone is not enough over the top of it.
    private static final Color CHROME = new Color(0f, 0f, 0f, 0.42f);

    private final List<PotTile> tiles = new ArrayList<>();

    private Group slots;
    private WalletBar wallet;
    private int potCheckFrame;

    public GreenhouseScreen(GdxContext context) {
        super(context);
    }

    // The panels on the other menus need the entrance to be gentle. Here the whole greenhouse arrives, so
    // it starts a touch smaller and settles in.
    @Override
    protected float entranceScale() {
        return 0.97f;
    }

    @Override
    protected void build(Table root) {
        addGardenBackdrop();

        slots = new Group();
        Stack layers = new Stack();
        layers.add(slots);
        layers.add(chrome());

        root.setFillParent(true);
        root.add(layers).grow();

        buildSlots();
    }

    // The greenhouse, filling the screen, under the drifting motes.
    //
    // Inserted at index 0 because MenuScreen scatters the sparkles into `ambient` in its constructor and
    // this runs later -- in a greenhouse they read as dust in the sunbeams, so they are worth keeping
    // above the art rather than below it. The layer is sized by hand because `ambient` is a plain Group
    // and lays nothing out, which is the whole reason it is a Group.
    private void addGardenBackdrop() {
        TextureRegion art = region(BACKDROP);
        if (art == null) {
            return;   // the title backdrop MenuScreen already drew stays; the screen still opens
        }
        Image garden = new Image(new TextureRegionDrawable(art));
        garden.setBounds(offsetX(), offsetY(), BG_WIDTH * scale(), BG_HEIGHT * scale());
        ambient.addActorAt(0, garden);
    }

    // ---- the transform the pots and the background share ----------------------------------------
    //
    // Scaling.fill, computed here rather than handed to an Image, because the pots have to be placed
    // through the very same numbers. The background is 2.26:1 against a 16:9 stage, so filling crops its
    // left and right edges -- harmless, the mats sit well inside the middle.

    private static float scale() {
        return Math.max(PvZGame.VIRTUAL_WIDTH / BG_WIDTH, PvZGame.VIRTUAL_HEIGHT / BG_HEIGHT);
    }

    private static float offsetX() {
        return (PvZGame.VIRTUAL_WIDTH - BG_WIDTH * scale()) / 2f;
    }

    private static float offsetY() {
        return (PvZGame.VIRTUAL_HEIGHT - BG_HEIGHT * scale()) / 2f;
    }

    // A point in the background's pixels becomes a point on the stage. The y flip is the whole reason
    // this exists: image coordinates run down from the top, Scene2D's run up from the bottom.
    private static float stageX(float backgroundX) {
        return offsetX() + backgroundX * scale();
    }

    private static float stageY(float backgroundY) {
        return offsetY() + (BG_HEIGHT - backgroundY) * scale();
    }

    // ---- the twelve tiles -----------------------------------------------------------------------

    private void buildSlots() {
        PotTile.PotArt art = new PotTile.PotArt(region(POT_ART), region(POT_RIPE_ART),
                region(LOCK_ART), region(BANNER_ART), region(GEM_ART));
        PotTile.PotActions actions = actions();

        float tileWidth = SLOT_WIDTH * scale();
        float tileHeight = SLOT_HEIGHT * scale();

        // Back row first, so the front rows draw OVER the mats behind them. That is what the perspective
        // in the painting requires: a plant in row 3 stands in front of row 2's mat, and adding the rows
        // in reading order is what makes it look that way.
        for (int row = 0; row < SLOT_FLOOR_Y.length; row++) {
            for (int col = 0; col < SLOT_X.length; col++) {
                // The 1-based pair the commands take, captured here rather than read back off the tile:
                // these are the same two numbers the command string wants, and taking them from one place
                // is what stops a (row, col) slip from reaching the model.
                final int potX = col + 1;
                final int potY = row + 1;
                PotTile tile = new PotTile(skin, potX, potY, tileWidth, tileHeight, art, actions);
                tile.setPosition(stageX(SLOT_X[col]) - tileWidth / 2f, stageY(SLOT_FLOOR_Y[row]));
                slots.addActor(tile);
                tiles.add(tile);
            }
        }
    }

    private PotTile.PotActions actions() {
        return new PotTile.PotActions() {
            @Override
            public void buy(int potX, int potY) {
                buyPot();
            }

            @Override
            public void plant(int potX, int potY) {
                commands.submit("plant pot at (" + potX + ", " + potY + ")");
            }

            @Override
            public void hurry(int potX, int potY) {
                offerSpeedUp(potX, potY);
            }

            @Override
            public void harvest(int potX, int potY) {
                collect(potX, potY);
            }
        };
    }

    // ---- acting on a pot ------------------------------------------------------------------------

    // Buying a pot, from a screen that cannot sell one.
    //
    // The model sells pots in the SHOP, and "shop buy" is a shop-menu command -- posted from here it
    // would fall through the router and do nothing. So the button walks the route a player would: into
    // the shop, buy one pot, back out again. All three are real commands and all three are legal in that
    // order, they run inside one frame so the shop is never seen, and every rule about price, funds and
    // a full greenhouse stays the model's. The price in the question is read off the ShopItem, so it
    // cannot go stale.
    private void buyPot() {
        int price = potPrice();
        Profile profile = profile();
        String cost = price < 0 ? "the going rate" : price + " coins";
        ConfirmDialog.show(stage, context.assets(), skin, "One More Pot",
                "Buy another pot for " + cost + "? You have "
                        + (profile == null ? 0 : profile.getCoins()) + ".",
                "Buy It", () -> {
                    commands.submit("enter shop");
                    commands.submit("shop buy -i " + SHOP_ITEM_POT + " -n 1");
                    commands.submit("menu exit");
                });
    }

    private int potPrice() {
        models.shop.Shop shop = context.appSession().getShop();
        if (shop == null || shop.getPermanentItems() == null) {
            return -1;
        }
        for (ShopItem item : shop.getPermanentItems()) {
            if (item.getId() == SHOP_ITEM_POT) {
                return item.getPrice();
            }
        }
        return -1;
    }

    // The gem price is the pot's own -- one gem per hour still to run, rounded up -- so it falls as the
    // plant grows and this never quotes a stale number.
    private void offerSpeedUp(int potX, int potY) {
        Pot pot = pot(potX, potY);
        if (pot == null) {
            return;
        }
        int cost = pot.getRemainingHoursCeil();
        Profile profile = profile();
        int held = profile == null ? 0 : profile.getGems();
        ConfirmDialog.show(stage, context.assets(), skin, "In a Hurry?",
                "Finish " + plantName(pot) + " right now for " + cost + " gem"
                        + (cost == 1 ? "" : "s") + "? You have " + held + ".",
                "Use the Gems",
                () -> commands.submit("grow (" + potX + ", " + potY + ")"));
    }

    // Harvest, then say what came of it.
    //
    // The reward is NOT recomputed here: the coin balance before and after the command is what the
    // notice reports, so the screen never gets a second opinion about what a Marigold is worth. A collect
    // the model refused leaves the plant in the pot, and then there is nothing to celebrate.
    private void collect(int potX, int potY) {
        Pot pot = pot(potX, potY);
        if (pot == null) {
            return;
        }
        String plant = plantName(pot);
        Profile profile = profile();
        int before = profile == null ? 0 : profile.getCoins();

        commands.submit("collect (" + potX + ", " + potY + ")");
        if (pot.getOnPot() != null) {
            return;   // refused; the model's own toast has already said why
        }
        int gained = (profile == null ? 0 : profile.getCoins()) - before;
        String reward = gained > 0
                ? "You picked up " + gained + " coins."
                : "Its seed packet is boosted -- that one starts your next lawn ahead.";
        ConfirmDialog.announce(stage, context.assets(), skin, "Harvest!",
                plant + ", pulled up and potted. " + reward, "Lovely");
    }

    // ---- per-frame model read -------------------------------------------------------------------

    @Override
    protected void refresh() {
        runPotCheck();
        Profile profile = profile();
        wallet.refresh(profile);

        for (PotTile tile : tiles) {
            Pot pot = pot(tile.potX(), tile.potY());
            if (pot == null) {
                continue;
            }
            // The one call that turns a finished timer into a ripe pot. ShowGreenhouseCommand does the
            // same before printing; here it has to happen every frame, because nobody is typing.
            pot.updateState();
            GreenHousePlant growing = pot.getOnPot();
            String name = growing == null ? null : growing.getName();
            tile.show(pot.getState(), name, pot.getRemainingTimeFormatted(),
                    pot.getRemainingHoursCeil(), sprite(name));
        }
    }

    // The one place a 1-based (column, row) pair becomes a pot. GreenHouse is 0-based throughout, so the
    // conversion lives exactly here and nowhere else.
    private Pot pot(int potX, int potY) {
        GreenHouse greenhouse = greenhouse();
        if (greenhouse == null || !greenhouse.isValidCoordinate(potX - 1, potY - 1)) {
            return null;
        }
        return greenhouse.getPot(potX - 1, potY - 1);
    }

    private EntitySprite sprite(String plantName) {
        String display = displayName(plantName);
        return display == null ? null : context.sprites().get(display);
    }

    private static String plantName(Pot pot) {
        GreenHousePlant plant = pot.getOnPot();
        String display = plant == null ? null : displayName(plant.getName());
        return display == null ? "That plant" : display;
    }

    // The plant's own spelling, for both the art and the sentences about it.
    //
    // PlantPotCommand takes its random pick from Profile.getUnlockedPlants(), which stores names
    // lower-cased -- so without this the game offers to hurry along "gold bloom" and the sprite registry
    // is asked for a plant by a name no template uses. "Marigold" is not in plants.json at all and
    // resolves on its own, which is why an unknown name is passed through rather than dropped.
    private static String displayName(String plantName) {
        if (plantName == null || plantName.isBlank()) {
            return null;
        }
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(plantName);
        return template == null ? plantName : template.getName();
    }

    // ---- chrome ---------------------------------------------------------------------------------

    // Title and wallet along the top, the two buttons along the bottom, nothing in between -- the middle
    // belongs to the artwork and the pots standing on it.
    private Table chrome() {
        Table layer = new Table();
        layer.top();
        layer.add(header()).growX().row();
        layer.add().grow().row();
        layer.add(footer()).growX();
        return layer;
    }

    private Table header() {
        wallet = new WalletBar(skin);

        Table bar = new Table();
        bar.setBackground(context.assets().solid(CHROME));
        bar.pad(8f, 26f, 8f, 26f);
        bar.add(MenuStyles.title(skin, "Greenhouse")).left();
        bar.add(subtitle("Plant a pot, wait a while, come back for something good."))
                .padLeft(18f).left();
        bar.add().expandX();
        bar.add(wallet).right();
        return bar;
    }

    private Table footer() {
        TextButton store = MenuStyles.button(skin, "Store", MenuStyles.BUTTON_GREEN);
        store.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                commands.submit("enter shop");
            }
        });

        TextButton back = MenuStyles.button(skin, "Back", MenuStyles.BUTTON_PURPLE);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                goBack();
            }
        });

        Table bar = new Table();
        bar.setBackground(context.assets().solid(CHROME));
        bar.pad(10f, 26f, 10f, 26f);
        bar.add().expandX();
        bar.add(store).width(200f).height(52f).padRight(12f);
        bar.add(back).width(170f).height(52f);
        return bar;
    }

    private Label subtitle(String text) {
        Label label = MenuStyles.label(skin, text, MenuStyles.TEXT);
        label.setColor(DIM);
        label.setFontScale(0.8f);
        return label;
    }

    // ---- harness --------------------------------------------------------------------------------

    private static final int POT_CHECK_FRAME = 30;

    // -Dpvz.potCheck=1|2|3. See DebugFlags: a growing pot and a ripe one are otherwise two and eight
    // hours away, and no screenshot run can wait for either. Every hop is a real command, so this is a
    // check of the plant, grow and collect paths and not only of how they look.
    private void runPotCheck() {
        if (DebugFlags.POT_CHECK < 1 || potCheckFrame < 0) {
            return;
        }
        if (++potCheckFrame < POT_CHECK_FRAME) {
            return;
        }
        potCheckFrame = -1;
        // Clear the first row before planting into it. Without this the check quietly stops working the
        // second time it is run: a pot left ripe by the previous run refuses to be planted, so the run
        // that was supposed to produce a GROWING pot produced three ripe ones and no timer banner at all.
        // A collect on a pot that is not ready is refused harmlessly, so this needs no state to be true.
        for (int col = 1; col <= SLOT_X.length; col++) {
            commands.submit("collect (" + col + ", 1)");
        }
        commands.submit("plant pot at (1, 1)");   // -> GROWING, with a live timer and a gem price
        commands.submit("plant pot at (2, 1)");
        commands.submit("grow (2, 1)");           // -> READY, in the gold pot
        com.badlogic.gdx.Gdx.app.log("PotCheck",
                "row 1 cleared; (1,1) growing, (2,1) ripe, (3,1) empty, (4,1) whatever it was");
        // One dialog per run, not both: a second opens on top of the first and the screenshot then shows
        // a stack rather than the thing being checked.
        if (DebugFlags.POT_CHECK == 2) {
            collect(2, 1);      // the ripe pot -> harvest, and the reward notice
        } else if (DebugFlags.POT_CHECK == 3) {
            offerSpeedUp(1, 1); // the growing pot -> the gem speed-up dialog
        } else if (DebugFlags.POT_CHECK == 4) {
            checkBuySequence();
        }
    }

    // -Dpvz.potCheck=4: runs the Buy button's command sequence and reports whether a pot actually opened.
    //
    // This is the one path a screenshot cannot judge, and the one most likely to break: three commands
    // across two menus, where "shop buy" is only legal in the middle one. If the router ever stops
    // accepting one of them the pot count simply will not move, silently -- exactly the failure the other
    // harness flags exist to make loud. The dialog is skipped; this checks the commands, not the prompt.
    private void checkBuySequence() {
        GreenHouse greenhouse = greenhouse();
        if (greenhouse == null) {
            return;
        }
        int before = greenhouse.getUnlockedPots().size();
        commands.submit("enter shop");
        commands.submit("shop buy -i " + SHOP_ITEM_POT + " -n 1");
        commands.submit("menu exit");
        int after = greenhouse.getUnlockedPots().size();
        com.badlogic.gdx.Gdx.app.log("PotCheck", "buy sequence: unlocked pots " + before + " -> " + after
                + (after > before ? "" : "   *** the purchase did nothing ***")
                + " | menu now " + context.appSession().getCurrentMenu());
    }

    private GreenHouse greenhouse() {
        Profile profile = profile();
        return profile == null ? null : profile.getMyGreenHouse();
    }

    private Profile profile() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getProfile();
    }
}
