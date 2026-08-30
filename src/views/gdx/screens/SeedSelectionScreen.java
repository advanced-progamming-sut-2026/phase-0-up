package views.gdx.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import controllers.commands.collection.UpgradePlantCommand;
import controllers.commands.seedselection.StartLevelCommand;
import models.game.GameSession;
import models.game.SeedPacket;
import models.game.gamemodes.GameMode;
import models.templates.PlantTemplate;
import models.user.Profile;
import models.user.User;
import utils.Constants;
import utils.registry.PlantRegistry;
import views.gdx.core.DebugFlags;
import views.gdx.core.GdxContext;
import views.gdx.ui.ButtonJuice;
import views.gdx.ui.MenuStyles;
import views.gdx.ui.PlantIcon;
import views.gdx.ui.SeedCardActor;
import views.gdx.ui.UiArt;
import views.gdx.ui.CurrencyHUD;

import java.util.ArrayList;
import java.util.List;

// Picking the loadout before a level starts.
//
// The screen is three bands: the seed bar you are filling, the plant you are looking at with what you
// can do to it, and everything the level offers. Cards are the same SeedCardActor the in-game bank
// uses, so a plant looks identical here and on the lawn -- including its boosted frame and its sun
// price, which is the whole reason the card is shared rather than re-drawn per screen.
//
// Clicking a card posts "add plant -t <name>" or "remove plant -t <name>"; the seed bar's size, which
// plants the level offers and which are locked are all the model's answers, not this screen's.
//
// Two actions do NOT go through the router, for two different reasons:
//
//   "Let's Rock"  -- the typed "start game" runs StartLevelCommand and then GameEngine.startLoop(),
//                    the terminal's blocking loop, which would freeze the window. So the Command runs
//                    on its own, and moving to IN_GAME lets ScreenManager hand over to GameScreen,
//                    which drives the same engine from the render loop instead.
//   "Upgrade"     -- "menu collection upgrade-plant" is legal only inside the collection menu, and
//                    EnterMenuCommand has no edge out of the plants menu to get there and back. The
//                    Command itself is front-end agnostic, so it is constructed directly against the
//                    Renderers seam, exactly as StartLevelCommand is.
public final class SeedSelectionScreen extends MenuScreen {

    // Eight cards across, which is both the usual seed-bar size and a comfortable grid width.
    private static final int CARDS_PER_ROW = 8;
    private static final float CARD_GAP = 6f;

    // Bigger than the 74x92 the in-game bank draws, and deliberately so: the bank is squeezed in beside
    // a sun counter and a shovel, this screen is a whole panel with one job. SeedCardActor grows its
    // portrait to whatever cell it is given, so this is a size and not a second card.
    private static final float TILE_WIDTH = 92f;
    private static final float TILE_HEIGHT = 115f;
    private static final float ROW_WIDTH = TILE_WIDTH * CARDS_PER_ROW + CARD_GAP * (CARDS_PER_ROW - 1);

    // A ceiling, not a size: a level offering nine plants scrolls rather than pushing the panel's frame
    // off the bottom of a 720-unit screen, and one offering four does not leave a hole under them.
    private static final float GRID_HEIGHT = 250f;

    // The detail strip's fixed costs, which together with the two buttons have to leave the stats line
    // enough room to say its piece. Everything in that strip overflows silently if they do not: a Table
    // whose cell is narrower than its minimum width does not clip, it spills out of both ends.
    private static final float PORTRAIT = 50f;
    private static final float ACTION_WIDTH = 190f;
    private static final float ACTION_HEIGHT = 44f;
    private static final float ACTION_FONT = 0.6f;

    private static final Color DIM = new Color(0.86f, 0.84f, 0.80f, 1f);
    private static final Color BAND = new Color(0f, 0f, 0f, 0.30f);
    private static final Color GOLD = new Color(1f, 0.88f, 0.45f, 1f);

    private final UiArt art;

    private Table available;
    private Table bar;
    private Label slotsLabel;
    private CurrencyHUD wallet;

    // The plant the two action buttons act on. Follows the pointer across the cards, because a screen
    // where you must first select a plant and then press Boost has two steps where the player expects
    // one -- and "selected" already means "on the seed bar" here.
    private String focused;

    // The plant whose name and portrait are currently in the strip. See refreshDetail.
    private String shownFocus;

    private Image focusPortrait;
    private Label focusName;
    private Label focusStats;
    private TextButton boostButton;
    private TextButton upgradeButton;

    public SeedSelectionScreen(GdxContext context) {
        super(context);
        this.art = new UiArt(context.assets());
    }

    // The soundtrack has a "Choose Your Seeds" cue, and this is the screen it is for.
    @Override
    protected String musicTrack() {
        return views.gdx.core.AudioManager.MUSIC_SEED_SELECT;
    }

    // Lays its own out, in the header beside the prices it explains.
    @Override
    protected boolean showsOwnCurrency() {
        return true;
    }

    @Override
    protected void build(Table root) {
        Table panel = MenuPanel.build(skin, "Choose Your Plants");

        panel.add(barHeader()).width(ROW_WIDTH).padBottom(4f).row();

        bar = new Table();
        // Left, not centred: a level with seven slots leaves a gap on the right, and centring it would
        // float the bar half a card away from the strip and the grid it sits between.
        bar.left();
        panel.add(bar).width(ROW_WIDTH).height(TILE_HEIGHT).padBottom(10f).row();

        panel.add(detailBar()).width(ROW_WIDTH).padBottom(12f).row();

        available = new Table();
        available.top().left();
        ScrollPane pane = new ScrollPane(available, skin);
        pane.setScrollingDisabled(true, false);
        pane.setFadeScrollBars(false);
        pane.setOverscroll(false, false);
        panel.add(pane).width(ROW_WIDTH + 18f).maxHeight(GRID_HEIGHT).padBottom(14f).row();

        panel.add(actions()).row();

        root.setFillParent(true);
        root.add(panel);

        rebuild();
    }

    // The bar's own caption, with the wallet on the other end -- both prices on this screen are paid
    // out of it, so the balance belongs where the buttons that spend it are.
    private Table barHeader() {
        slotsLabel = MenuStyles.label(skin, "", MenuStyles.TEXT);
        slotsLabel.setAlignment(Align.left);

        wallet = new CurrencyHUD(skin, debugCheat("coin"), debugCheat("gem"));

        Table header = new Table();
        header.add(slotsLabel).left();
        header.add().expandX();
        header.add(wallet).right();
        return header;
    }

    // ---- the plant you are looking at ------------------------------------------------------------

    // One strip carrying everything about a single plant that will not fit on a card: its level, how
    // many seed packets it has banked, whether it is boosted, and the two things you can spend on it.
    private Table detailBar() {
        focusPortrait = new Image();
        focusPortrait.setScaling(Scaling.fit);
        focusName = MenuStyles.label(skin, "", MenuStyles.BODY_BIG);
        focusName.setAlignment(Align.left);
        focusStats = MenuStyles.label(skin, "", MenuStyles.TEXT);
        focusStats.setAlignment(Align.left);
        focusStats.setFontScale(0.7f);
        focusStats.setColor(DIM);

        Table text = new Table();
        text.add(focusName).left().row();
        text.add(focusStats).left().padTop(2f);

        Table strip = new Table();
        strip.setBackground(context.assets().solid(BAND));
        strip.pad(8f, 12f, 8f, 12f);
        strip.add(focusPortrait).size(PORTRAIT).padRight(10f);
        strip.add(text).left();
        strip.add().expandX();
        strip.add(boost()).width(ACTION_WIDTH).height(ACTION_HEIGHT).padRight(8f);
        strip.add(upgrade()).width(ACTION_WIDTH).height(ACTION_HEIGHT);
        return strip;
    }

    private TextButton boost() {
        boostButton = MenuStyles.button(skin, "Boost", MenuStyles.BUTTON_GREEN_SMALL);
        boostButton.getLabel().setFontScale(ACTION_FONT);
        boostButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (focused != null) {
                    // Legal in the plants menu, so it takes the ordinary route: the command decides
                    // whether the player owns the packet, has the gems, or boosted it already.
                    commands.submit("boost plant -t " + focused);
                    rebuild();
                }
            }
        });
        return boostButton;
    }

    private TextButton upgrade() {
        upgradeButton = MenuStyles.button(skin, "Upgrade", MenuStyles.BUTTON_BROWN);
        upgradeButton.getLabel().setFontScale(ACTION_FONT);
        upgradeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                upgradeFocused();
            }
        });
        return upgradeButton;
    }

    // Constructed rather than submitted -- see the class comment. Every rule about coins, packets and
    // the level ceiling stays inside CollectionSystem, which is where the collection menu reads it too.
    private void upgradeFocused() {
        User user = context.appSession().getCurrentUser();
        if (focused == null || user == null) {
            return;
        }
        new UpgradePlantCommand(focused, user, context.renderers().collectionMenu()).execute();
        rebuild();
    }

    private void focus(String plantName) {
        this.focused = plantName;
        refreshDetail();
    }

    // Reads the profile every frame, so a boost or an upgrade is reflected the instant it lands rather
    // than only after the next rebuild.
    //
    // The name and the portrait are set only when the focus actually MOVES. Both are cheap, but the
    // portrait means wrapping a region in a fresh Drawable, and doing that sixty times a second for a
    // picture that has not changed is sixty pieces of garbage a second for nothing.
    private void refreshDetail() {
        Profile profile = profile();
        if (focused == null || profile == null) {
            focusName.setText("");
            focusStats.setText("Hover a plant to see what you can do with it.");
            focusPortrait.setDrawable(null);
            boostButton.setText("Boost");
            upgradeButton.setText("Upgrade");
            shownFocus = null;
            return;
        }
        if (!focused.equals(shownFocus)) {
            shownFocus = focused;
            PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(focused);
            focusName.setText(template == null ? focused : template.getName());
            focusPortrait.setDrawable(portraitOf(focused));
        }

        String key = focused.toLowerCase(java.util.Locale.ROOT).trim();
        int level = profile.getPlantsLevels() == null ? 1
                : profile.getPlantsLevels().getOrDefault(key, 1);
        int packets = profile.getOwnedSeedPackets() == null ? 0
                : profile.getOwnedSeedPackets().getOrDefault(key, 0);
        boolean boosted = profile.isSeedBoosted(focused);

        focusStats.setText(statsOf(level, packets, boosted));
        focusStats.setColor(boosted ? GOLD : DIM);
        boostButton.setText(boosted ? "Boosted"
                : "Boost  " + Constants.BOOST_PLANT_COST_GEMS + " gems");
        upgradeButton.setText(level >= Constants.PLANT_MAX_LEVEL ? "Max level"
                : "Upgrade  " + Constants.UPGRADE_PLANT_COST_COINS + " coins");
    }

    private String statsOf(int level, int packets, boolean boosted) {
        // No sun cost here: the card next to it already shows one, with the coin, and repeating it is
        // what pushed this line into the buttons.
        StringBuilder text = new StringBuilder();
        text.append("Level ").append(level).append("  -  ")
                .append(packets).append('/').append(Constants.UPGRADE_PLANT_REQUIRED_SEED_PACKETS)
                .append(" packets");
        if (boosted) {
            text.append("  -  BOOSTED");
        }
        return text.toString();
    }

    private TextureRegionDrawable portraitOf(String plantName) {
        TextureRegion region = art.packet(plantName);
        return region == null ? null : new TextureRegionDrawable(region);
    }

    // ---- the two rows of cards -------------------------------------------------------------------

    // Both rows, from scratch. Adding or removing a seed changes which row a plant belongs to, and
    // rebuilding is cheaper to get right than moving actors between two tables.
    private void rebuild() {
        GameSession session = session();
        available.clearChildren();
        bar.clearChildren();
        if (session == null) {
            slotsLabel.setText("No level loaded.");
            return;
        }

        List<SeedPacket> selected = session.getSelectedSeeds();
        int slots = session.getMaxSeedSlots();
        slotsLabel.setText(imitating
                ? "Pick the plant your Imitater should copy  -  click him again to change your mind"
                : barCaption(selected.size(), slots, baseSlots(session)));

        buildBar(selected, slots, baseSlots(session));
        buildGrid(session);

        if (focused == null) {
            focus(selected.isEmpty() ? firstOffered(session) : selected.get(0).getPlantType());
        } else {
            refreshDetail();
        }
    }

    // Every slot the LEVEL was authored with, filled, empty or welded shut.
    //
    // The empty ones are drawn rather than left as a gap, because "3 of 8" is a sentence and five empty
    // frames are a picture -- and the picture is the one the player reads while deciding whether to keep
    // picking. The welded-shut ones are drawn for a sharper reason: `Locked Plants` type 1 works by
    // shrinking `getMaxSeedSlots()`, so a bar that only draws what the mode allows is indistinguishable
    // from a level that simply has fewer slots, and the whole mechanic disappears.
    private void buildBar(List<SeedPacket> selected, int slots, int authored) {
        int total = Math.max(slots, authored);
        for (int slot = 0; slot < total; slot++) {
            Actor tile;
            if (slot < selected.size()) {
                SeedPacket packet = selected.get(slot);
                tile = card(packet.getPlantType(), true, packet.isImitated());
            } else {
                tile = SeedCardActor.emptySlot(art, slot < slots ? null : lockArt());
            }
            bar.add(tile).size(TILE_WIDTH, TILE_HEIGHT)
                    .padRight(slot == total - 1 ? 0f : CARD_GAP);
        }
    }

    private com.badlogic.gdx.scenes.scene2d.utils.Drawable lockArt() {
        return MenuStyles.drawable(skin, SeedCardActor.LOCK_ART);
    }

    // How many slots the level was authored with, before a mode shut any.
    //
    // Read from the template rather than remembered, because a mini-game level has no template at all
    // and its slot count IS the default.
    private static int baseSlots(GameSession session) {
        if (session.getLevel() == null || session.getLevel().getTemplate() == null) {
            return Constants.DEFAULT_SEED_SLOTS;
        }
        return session.getLevel().getTemplate().getSeedSlots();
    }

    private static String barCaption(int filled, int slots, int authored) {
        String text = "Your seed bar:  " + filled + " of " + slots + " slots filled";
        if (authored > slots) {
            text += "   -   " + (authored - slots)
                    + (authored - slots == 1 ? " slot welded shut" : " slots welded shut");
        }
        return text;
    }

    // Wrapped. A level can offer more plants than fit across the panel, and an unwrapped row does not
    // scroll or clip -- it pushes the panel's own frame off both edges of the screen.
    private void buildGrid(GameSession session) {
        int column = 0;
        for (String plantName : offeredPlants(session)) {
            if (!showInGrid(session, plantName)) {
                continue;
            }
            available.add(card(plantName, false)).size(TILE_WIDTH, TILE_HEIGHT)
                    .padRight(CARD_GAP).padBottom(CARD_GAP);
            if (++column % CARDS_PER_ROW == 0) {
                available.row();
            }
        }
        if (column == 0) {
            available.add(MenuStyles.label(skin, "Every plant this level offers is already on the bar.",
                    MenuStyles.TEXT)).pad(24f);
        }
    }

    // ---- the Imitater ----------------------------------------------------------------------------
    //
    // Picking it is two clicks, not one: the Imitater, then the plant it should copy. While the first
    // has happened and the second has not, this is true -- the grid stops hiding plants already on the
    // bar (those are the ones worth a second packet of) and the caption says what it is waiting for.
    // Clicking the Imitater again backs out.
    private boolean imitating;

    private static boolean isImitater(String plantName) {
        return PlantRegistry.getInstance().isImitater(plantName);
    }

    private static String imitaterName() {
        PlantTemplate template = PlantRegistry.getInstance().getImitaterTemplate();
        return template == null ? "Imitater" : template.getName();
    }

    // Which offered plants belong in the lower row right now. Normally the ones not already on the bar;
    // while the Imitater is waiting to be told what to copy, all of them. The Imitater itself drops out
    // of the row once it has been spent -- there is only one.
    private boolean showInGrid(GameSession session, String plantName) {
        if (isImitater(plantName)) {
            return session.getImitatedSeed() == null;
        }
        return imitating || !session.isSeedSelected(plantName);
    }

    // What one click on a card means, given where the card is and whether the Imitater is waiting.
    private void clickCard(String plantName, boolean onBar, boolean imitated) {
        if (onBar) {
            commands.submit("remove plant -t " + (imitated ? imitaterName() : plantName));
        } else if (isImitater(plantName)) {
            imitating = !imitating;   // arm it, or back out of it
        } else if (imitating) {
            imitating = false;
            commands.submit("imitate plant -t " + plantName);
        } else {
            commands.submit("add plant -t " + plantName);
        }
        rebuild();
    }

    private SeedCardActor card(String plantName, boolean onBar) {
        return card(plantName, onBar, false);
    }

    // A card that adds on click when it is in the available row, and removes when it is on the bar.
    // Hovering it moves the detail strip, which is what makes Boost and Upgrade reachable in one move.
    private SeedCardActor card(String plantName, boolean onBar, boolean imitated) {
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(plantName);
        int cost = template == null ? 0 : template.getCost();
        int recharge = template == null ? 0 : (int) template.getRecharge();

        // A throwaway packet, so the card can carry the boost without the screen writing to the
        // model's own. Seed-selection boosts live on the Profile until GameEngine copies them onto the
        // live packets (GameSession.applySeedBoosts), so the Profile is what this has to read.
        SeedPacket packet = new SeedPacket(plantName, recharge);
        Profile profile = profile();
        packet.setBoosted(profile != null && profile.isSeedBoosted(plantName));

        SeedCardActor actor = new SeedCardActor(context.assets(), art, packet, cost,
                new PlantIcon(context.sprites().get(plantName)));
        actor.setSelected(onBar);
        // A padlock on a seed the mode has bolted to the bar. The refusal itself is
        // ToggleSeedCommand's; this only stops the card from looking like something you can take off.
        GameMode mode = session() == null ? null : session().getMode();
        actor.setLocked(mode != null && mode.isSeedForced(plantName));
        // The Imitater's own packet in the far corner of the card it is dressed as, so two Peashooters
        // on the bar are not two identical cards one of which mysteriously answers to another name.
        if (imitated) {
            actor.setImitated(portraitOf(imitaterName()));
        }
        // The same swell-and-press the menu buttons have. A seed card is the most clicked thing on this
        // screen and was the only one that gave nothing back when the pointer was over it.
        ButtonJuice.applyTo(actor);
        actor.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                clickCard(plantName, onBar, imitated);
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor from) {
                if (pointer == -1) {
                    focus(plantName);
                }
            }
        });
        return actor;
    }

    // ---- what the level offers -------------------------------------------------------------------

    // What this level actually offers, matched against what the player owns.
    //
    // The same pairing ConsolePlantMenuRenderer does, and case-insensitively for the same reason: the
    // profile stores plant names lower-cased while the level's pool uses display names.
    private List<String> offeredPlants(GameSession session) {
        Profile profile = session.getPlayer();
        List<String> pool = session.getLevel() == null ? null : session.getLevel().getAvailablePlants();
        if (pool == null || profile == null) {
            return List.of();
        }
        List<String> owned = profile.getUnlockedPlants();
        List<String> result = new ArrayList<>();
        for (String plantName : pool) {
            for (String have : owned) {
                if (have.equalsIgnoreCase(plantName)) {
                    result.add(plantName);
                    break;
                }
            }
        }
        return result;
    }

    private String firstOffered(GameSession session) {
        List<String> offered = offeredPlants(session);
        return offered.isEmpty() ? null : offered.get(0);
    }

    // ---- the way out -----------------------------------------------------------------------------

    private Table actions() {
        Table row = new Table();
        row.add(startButton()).width(250f).height(64f).padRight(12f);
        row.add(backButton()).width(180f).height(50f);
        return row;
    }

    // Wrapped in a Container so it can breathe.
    //
    // The pulse has to live on the WRAPPER: ButtonJuice calls clearActions() on the button itself on
    // every hover and press, so a forever-action attached to the button would be cancelled the first
    // time the pointer touched it -- and the hover scale then multiplies with the idle one instead of
    // fighting it, which is what makes the press feel like it lands.
    private Actor startButton() {
        TextButton button = MenuStyles.button(skin, "Let's Rock!", MenuStyles.BUTTON_GREEN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                start();
            }
        });

        Container<TextButton> holder = new Container<>(button);
        holder.fill();
        holder.setTransform(true);
        holder.setOrigin(Align.center);
        holder.addAction(Actions.forever(Actions.sequence(
                Actions.scaleTo(1.04f, 1.04f, 0.9f, Interpolation.sine),
                Actions.scaleTo(1f, 1f, 0.9f, Interpolation.sine))));
        return holder;
    }

    private void start() {
        GameSession session = session();
        if (session == null) {
            context.toasts().error("No level loaded.");
            return;
        }
        if (session.getSelectedSeeds().isEmpty()) {
            context.toasts().error("Pick at least one plant -- bare hands won't stop a Gargantuar.");
            return;
        }
        // Sets the menu to IN_GAME and announces the start. GameScreen picks the session up from there.
        new StartLevelCommand(session, context.appSession(),
                context.renderers().plantMenu()).execute();
    }

    private TextButton backButton() {
        TextButton button = MenuStyles.button(skin, "Back", MenuStyles.BUTTON_BROWN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                goBack();
            }
        });
        return button;
    }

    private GameSession session() {
        return context.appSession().getCurrentGameSession();
    }

    private Profile profile() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getProfile();
    }

    // ---- unattended run --------------------------------------------------------------------------

    // Deliberately well BEFORE MenuScreen's own harness frame (30). Filling the bar changes how many
    // rows the grid needs, which changes the panel's height, which moves every widget in it -- and
    // -Dpvz.click reads a button's position before that frame's layout pass. Run in the same frame, the
    // click lands on where the button used to be and nothing happens, with the harness still reporting
    // a press.
    private static final int SEED_CHECK_FRAME = 15;

    private int seedCheckFrame;

    // -Dpvz.seedCheck=N: fills N slots and focuses the last one, so one capture carries filled cards,
    // empty slots and a detail strip that is not merely the screen's own opening guess.
    private void runSeedCheck() {
        if (DebugFlags.SEED_CHECK < 1 || seedCheckFrame < 0) {
            return;
        }
        if (++seedCheckFrame < SEED_CHECK_FRAME) {
            return;
        }
        seedCheckFrame = -1;
        GameSession session = session();
        if (session == null) {
            return;
        }
        String last = null;
        for (String plantName : offeredPlants(session)) {
            if (session.getSelectedSeeds().size() >= DebugFlags.SEED_CHECK) {
                break;
            }
            commands.submit("add plant -t " + plantName);
            last = plantName;
        }
        rebuild();
        if (last != null) {
            focus(last);
        }
    }

    @Override
    protected void refresh() {
        runSeedCheck();
        GameSession session = session();
        if (session != null) {
            // Selection can also change from outside this screen (a boost, a mode that pins a seed),
            // so the count is read live rather than only after a click. Through the same barCaption
            // rebuild() uses -- spelled out a second time here, it silently dropped Locked Plants'
            // "welded shut" tail on the very next frame after every rebuild.
            slotsLabel.setText(barCaption(session.getSelectedSeeds().size(),
                    session.getMaxSeedSlots(), baseSlots(session)));
        }
        wallet.refresh(profile());
        refreshDetail();
    }

    // Leaving seed selection abandons the level rather than resuming it, so the session goes with it.
    //
    // "menu exit", not "menu enter play". The two are not interchangeable: EnterMenuCommand walks an
    // explicit edge list and there is no edge OUT of the plants menu at all, so entering the play menu
    // from here was refused with "You can't enter play menu from plants menu!" and the button did
    // nothing but raise a toast. ExitMenuCommand is what knows the way back, and it already maps the
    // plants menu to the play menu -- the same route the terminal build takes.
    @Override
    protected void goBack() {
        context.appSession().setCurrentGameSession(null);
        commands.back();
    }
}
