package views.gdx.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import models.templates.PlantCategory;
import models.templates.PlantTemplate;
import models.templates.ZombieTemplate;
import models.user.Profile;
import models.user.User;
import utils.Constants;
import utils.registry.PlantRegistry;
import utils.registry.ZombieRegistry;
import views.gdx.core.GdxContext;
import views.gdx.ui.Cycler;
import views.gdx.ui.EntityCardActor;
import views.gdx.ui.EntityIcon;
import views.gdx.ui.EntityNames;
import views.gdx.ui.LiveEntityActor;
import views.gdx.ui.MenuStyles;
import views.gdx.ui.PlantIcon;
import views.gdx.ui.UiArt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// The almanac: every plant on file and every zombie in the game, what the player owns, and what they
// have survived meeting.
//
// One screen with two tabs rather than two screens, because the two halves are the same shape -- a grid
// of cards and a detail column -- and the model treats them as one menu. The grid reads the registries
// and the Profile directly; only buying and upgrading go through Commands, because those are the only
// two things here that change anything.
public final class CollectionScreen extends MenuScreen {

    private enum Tab { PLANTS, ZOMBIES }

    private static final int COLUMNS = 5;
    private static final float GRID_WIDTH = COLUMNS * (EntityCardActor.CARD_WIDTH + 10f) + 26f;
    private static final float GRID_HEIGHT = 396f;
    private static final float DETAIL_WIDTH = 330f;
    private static final int MAX_PLANT_LEVEL = 4;

    // A zombie the player has never met, drawn as its own outline. There is no silhouette art in the
    // dump and there does not need to be: the animation is drawn flat black, which is exactly what a
    // silhouette is.
    private static final Color SILHOUETTE = new Color(0.06f, 0.05f, 0.08f, 0.92f);
    private static final Color LOCKED_ART = new Color(0.45f, 0.45f, 0.50f, 1f);
    private static final Color DIM = new Color(0.78f, 0.76f, 0.72f, 1f);
    private static final Color COST = new Color(1f, 0.88f, 0.45f, 1f);
    private static final Color GEM = new Color(0.55f, 0.82f, 1f, 1f);
    private static final Color READY = new Color(0.55f, 0.85f, 0.45f, 1f);
    private static final Color MYSTERY = new Color(1f, 0.94f, 0.62f, 1f);
    private static final Color STAGE_BACK = new Color(0f, 0f, 0f, 0.35f);

    // The page's usable width: the column minus the scrollbar it now carries. Everything inside the
    // detail column measures against THIS, not DETAIL_WIDTH -- sized to the full column, the value at
    // the end of each stat row was clipped by the bar ("50" reading as "5").
    private static final float PAGE_WIDTH = DETAIL_WIDTH - 34f;

    // The live view at the top of the detail column.
    private static final float STAGE_HEIGHT = 132f;
    private static final float ICON_SIZE = 22f;
    // How far the detail column slides in from when the selection changes.
    private static final float DETAIL_SLIDE = 26f;
    private static final float DETAIL_FADE = 0.22f;

    // The skin's own padlock, laid over a locked plant's dimmed art.
    private static final String ICON_LOCK = "image_ui_cards_lock_medium";
    private static final String ICON_COINS = "image_ui_coins_stack_0";
    private static final String ICON_GEMS = "image_ui_gems_stack_1";

    // Plants unless -Dpvz.tab=zombies says otherwise. See DebugFlags.START_TAB.
    private Tab tab = "zombies".equalsIgnoreCase(views.gdx.core.DebugFlags.START_TAB)
            ? Tab.ZOMBIES : Tab.PLANTS;
    private PlantCategory family;          // null means every family
    private boolean ownedOnly;
    private boolean upgradableOnly;

    private String selectedPlant;          // template display name
    private String selectedZombie;         // registry alias

    private TextButton plantsTab;
    private TextButton zombiesTab;
    private Table filters;
    private Table grid;
    private Table detail;
    private com.badlogic.gdx.scenes.scene2d.Group detailSlider;
    private ScrollPane detailPane;
    private Label coins;
    private Label gems;

    public CollectionScreen(GdxContext context) {
        super(context);
    }

    @Override
    protected void build(Table root) {
        Table panel = MenuPanel.build(skin, "Almanac");

        Table head = new Table();
        head.add(tabs()).left();
        head.add().expandX();
        head.add(wallet()).right();
        panel.add(head).width(GRID_WIDTH + DETAIL_WIDTH + 20f).padBottom(8f).row();

        filters = new Table();
        panel.add(filters).width(GRID_WIDTH + DETAIL_WIDTH + 20f).padBottom(8f).row();

        grid = new Table();
        grid.top();
        ScrollPane pane = new ScrollPane(grid, skin);
        pane.setScrollingDisabled(true, false);
        pane.setFadeScrollBars(false);
        pane.setOverscroll(false, false);

        detail = new Table();
        detail.top();
        // Scrolled, because the page is taller than the panel for a plant that has everything: a live
        // view, six stats, a packet meter, a price and a button. Clipping it silently would hide the
        // Upgrade button, which is the one thing on the page the player came to press.
        detailPane = new ScrollPane(detail, skin);
        detailPane.setScrollingDisabled(true, false);
        detailPane.setFadeScrollBars(false);
        detailPane.setOverscroll(false, false);

        // The column lives inside a plain Group so the entrance can MOVE it -- see playDetailEntrance.
        detailSlider = new com.badlogic.gdx.scenes.scene2d.Group();
        detailPane.setFillParent(true);
        detailSlider.addActor(detailPane);

        Table body = new Table();
        body.add(pane).size(GRID_WIDTH, GRID_HEIGHT).padRight(20f).top();
        body.add(detailSlider).width(DETAIL_WIDTH).height(GRID_HEIGHT).top();
        panel.add(body).padBottom(12f).row();

        panel.add(backButton()).width(200f).height(54f).row();

        root.setFillParent(true);
        root.add(panel);

        buildFilters();
        rebuild();
        // Run on open as well as on every selection. Not decoration: it means the ordinary screenshot
        // of this screen exercises the entrance, which is the code path that broke the layout and was
        // invisible to a capture that never clicked anything.
        playDetailEntrance();
    }

    private Table tabs() {
        plantsTab = tabButton("Plants", Tab.PLANTS);
        zombiesTab = tabButton("Zombies", Tab.ZOMBIES);
        Table row = new Table();
        row.add(plantsTab).width(170f).height(50f).padRight(8f);
        row.add(zombiesTab).width(170f).height(50f);
        return row;
    }

    private TextButton tabButton(String text, Tab target) {
        TextButton button = MenuStyles.button(skin, text, MenuStyles.BUTTON_BROWN);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                tab = target;
                buildFilters();
                rebuild();
            }
        });
        return button;
    }

    // Coins and gems, in their own colours rather than both green: the two prices on this screen are
    // quoted in different currencies, and matching the number to its icon is what stops "1000 coins"
    // being read against a gem balance.
    private Table wallet() {
        coins = MenuStyles.label(skin, "0", MenuStyles.TEXT);
        coins.setColor(COST);
        gems = MenuStyles.label(skin, "0", MenuStyles.TEXT);
        gems.setColor(GEM);

        Table row = new Table();
        addWalletCell(row, ICON_COINS, coins);
        addWalletCell(row, ICON_GEMS, gems);
        return row;
    }

    private void addWalletCell(Table row, String iconId, Label value) {
        Drawable icon = MenuStyles.drawable(skin, iconId);
        if (icon != null) {
            row.add(new Image(icon)).size(30f).padRight(6f).padLeft(14f);
        }
        row.add(value).minWidth(70f).left();
    }

    private TextButton backButton() {
        TextButton back = MenuStyles.button(skin, "Back", MenuStyles.BUTTON_PURPLE);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                goBack();
            }
        });
        return back;
    }

    // The filter bar belongs to the plants tab only. A zombie has no family to filter on, is never
    // owned and is never upgradable -- three controls that would all be inert.
    private void buildFilters() {
        filters.clearChildren();
        if (tab != Tab.PLANTS) {
            return;
        }
        String[] families = familyOptions();
        Cycler cycler = new Cycler(skin, families, indexOfFamily());
        cycler.onChange(index -> {
            family = index == 0 ? null : PlantCategory.values()[index - 1];
            rebuild();
        });
        filters.add(cycler).left().padRight(20f);
        filters.add(MenuStyles.toggle(skin, "Owned", ownedOnly, on -> {
            ownedOnly = on;
            rebuild();
        })).width(180f).height(44f).padRight(8f);
        filters.add(MenuStyles.toggle(skin, "Upgradable", upgradableOnly, on -> {
            upgradableOnly = on;
            rebuild();
        })).width(220f).height(44f);
        filters.add().expandX();
    }

    private String[] familyOptions() {
        PlantCategory[] all = PlantCategory.values();
        String[] options = new String[all.length + 1];
        options[0] = "All Families";
        for (int i = 0; i < all.length; i++) {
            options[i + 1] = pretty(all[i].name());
        }
        return options;
    }

    private int indexOfFamily() {
        if (family == null) {
            return 0;
        }
        return family.ordinal() + 1;
    }

    private void rebuild() {
        boolean plants = tab == Tab.PLANTS;
        plantsTab.setStyle(skin.get(plants ? MenuStyles.BUTTON_GREEN : MenuStyles.BUTTON_BROWN,
                TextButton.TextButtonStyle.class));
        zombiesTab.setStyle(skin.get(plants ? MenuStyles.BUTTON_BROWN : MenuStyles.BUTTON_GREEN,
                TextButton.TextButtonStyle.class));

        grid.clearChildren();
        detail.clearChildren();
        if (plants) {
            ensurePlantSelection();
            buildPlantGrid();
            buildPlantDetail();
        } else {
            ensureZombieSelection();
            buildZombieGrid();
            buildZombieDetail();
        }
    }

    // The new page arriving rather than simply replacing the old one.
    //
    // The slide moves the PANE, which sits inside a plain Group that the body Table positions. That
    // distinction is the whole bug this replaced: moving the GROUP used absolute coordinates -- (26, 0)
    // then (0, 0) -- but the Table had already placed it at x = 700, so the entrance teleported the
    // whole detail column onto the left-hand grid and drew the two on top of each other. Inside the
    // group, (0, 0) really is the column's own corner.
    //
    // A Group lays nothing out, so the pane keeps whatever position an action gives it; setFillParent
    // only ever forces its SIZE. That is what makes the slide survive, where the same action on a
    // Table inside a layout would be undone by the next invalidate.
    private void playDetailEntrance() {
        if (detailPane == null) {
            return;
        }
        detailPane.clearActions();
        detailPane.getColor().a = 0f;
        detailPane.setPosition(DETAIL_SLIDE, 0f);
        detailPane.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.parallel(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(DETAIL_FADE,
                        Interpolation.fade),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.moveTo(0f, 0f, DETAIL_FADE,
                        Interpolation.sine)));
    }

    // An almanac that opens on a page is friendlier than one that opens on an instruction, so the
    // first visible card is selected when nothing is. It also re-selects when a filter hides whatever
    // was selected -- a detail column describing a plant that is no longer in the grid beside it is
    // worse than no selection at all.
    private void ensurePlantSelection() {
        PlantTemplate current = selectedPlant == null
                ? null : PlantRegistry.getInstance().getTemplateByName(selectedPlant);
        if (current != null && passesFilter(current)) {
            return;
        }
        selectedPlant = null;
        for (PlantTemplate template : plantsInOrder()) {
            if (passesFilter(template)) {
                selectedPlant = template.getName();
                return;
            }
        }
    }

    // Opens on the first zombie the player has actually MET, not simply the first in the list. The
    // list is alphabetical and its first entries are armour variants nobody meets early, so the
    // default page was reliably "??? Not yet encountered" -- a blank page as a welcome.
    private void ensureZombieSelection() {
        if (selectedZombie != null) {
            return;
        }
        List<String> aliases = zombiesInOrder();
        // -Dpvz.entity=<alias> wins. See DebugFlags: the grid scrolls and a screenshot run cannot reach
        // past its third row, so this is the only way to look at most of the roster unattended.
        String wanted = views.gdx.core.DebugFlags.ENTITY;
        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(wanted)) {
                selectedZombie = alias;
                return;
            }
        }
        for (String alias : aliases) {
            if (hasSeen(alias)) {
                selectedZombie = alias;
                return;
            }
        }
        selectedZombie = aliases.isEmpty() ? null : aliases.get(0);
    }

    // ---- plants -------------------------------------------------------------------------------

    private void buildPlantGrid() {
        int shown = 0;
        for (PlantTemplate template : plantsInOrder()) {
            if (!passesFilter(template)) {
                continue;
            }
            grid.add(plantCard(template))
                    .size(EntityCardActor.CARD_WIDTH, EntityCardActor.CARD_HEIGHT).pad(5f);
            if (++shown % COLUMNS == 0) {
                grid.row();
            }
        }
        if (shown == 0) {
            grid.add(MenuStyles.label(skin, "Nothing matches that filter.", MenuStyles.TEXT)).pad(40f);
        }
    }

    // By id, which is the order plants.json lists them in and therefore roughly the order the game
    // hands them out. Sorting by name would scatter the starter set through the alphabet.
    private List<PlantTemplate> plantsInOrder() {
        List<PlantTemplate> templates =
                new ArrayList<>(PlantRegistry.getInstance().getAllPlantTemplates().values());
        templates.sort(java.util.Comparator.comparingInt(PlantTemplate::getId));
        return templates;
    }

    private boolean passesFilter(PlantTemplate template) {
        if (family != null && template.getCategory() != family) {
            return false;
        }
        if (ownedOnly && !owns(template.getName())) {
            return false;
        }
        return !upgradableOnly || upgradable(template.getName());
    }

    private EntityCardActor plantCard(PlantTemplate template) {
        String name = template.getName();
        boolean owned = owns(name);
        EntityIcon icon = new PlantIcon(context.sprites().get(name));
        if (!owned) {
            icon.tinted(LOCKED_ART);
        }
        EntityCardActor card = new EntityCardActor(context.assets(), skin, art(icon),
                owned ? null : lockIcon(), name, plantFooter(name, owned), !owned);
        card.setSelected(name.equalsIgnoreCase(selectedPlant));
        card.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (name.equalsIgnoreCase(selectedPlant)) {
                    return;   // already the open page; re-animating it would read as a glitch
                }
                selectedPlant = name;
                rebuild();
                playDetailEntrance();
            }
        });
        return card;
    }

    // Nothing at all for a locked plant: the padlock on its art and the dimming already say so, and a
    // third statement of the same fact just crowds the tile.
    private String plantFooter(String name, boolean owned) {
        if (!owned) {
            return "";
        }
        return "Lv " + level(name) + "   " + packets(name) + "/"
                + Constants.UPGRADE_PLANT_REQUIRED_SEED_PACKETS;
    }

    // The skin's own padlock, over a locked plant's dimmed art.
    private Actor lockIcon() {
        Drawable art = MenuStyles.drawable(skin, ICON_LOCK);
        if (art == null) {
            return null;
        }
        Image lock = new Image(art);
        lock.setScaling(com.badlogic.gdx.utils.Scaling.fit);
        return lock;
    }

    // An undiscovered zombie's marker: a question mark centred on its own silhouette, pulsing slowly so
    // the card reads as "not yet" rather than as a blank.
    private Actor mysteryMark() {
        Label mark = MenuStyles.label(skin, "?", MenuStyles.TITLE);
        mark.setAlignment(Align.center);
        mark.setColor(MYSTERY);
        com.badlogic.gdx.scenes.scene2d.ui.Container<Label> box =
                new com.badlogic.gdx.scenes.scene2d.ui.Container<>(mark);
        box.setTransform(true);
        box.setOrigin(Align.center);
        box.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(0.45f, 1.1f,
                                Interpolation.sine),
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(1f, 1.1f,
                                Interpolation.sine))));
        return box;
    }

    private void buildPlantDetail() {
        PlantTemplate template = selectedPlant == null
                ? null : PlantRegistry.getInstance().getTemplateByName(selectedPlant);
        if (template == null) {
            detail.add(hint("Pick a plant to read its page.")).width(PAGE_WIDTH).pad(20f);
            return;
        }
        String name = template.getName();
        detail.add(stage(context.sprites().get(name))).size(PAGE_WIDTH, STAGE_HEIGHT)
                .padBottom(8f).row();
        detail.add(MenuStyles.label(skin, name, MenuStyles.HEADING))
                .width(PAGE_WIDTH).padBottom(2f).row();
        detail.add(subtitle(EntityNames.pretty(template.getCategory() == null
                ? "UNKNOWN" : template.getCategory().name()))).width(PAGE_WIDTH).padBottom(8f).row();

        Table stats = new Table();
        stat(stats, UiArt.SUN, "Sun cost", String.valueOf(template.getCost()));
        stat(stats, null, "Health", String.valueOf(template.getBaseHp()));
        stat(stats, null, "Damage", String.valueOf(template.getDamage()));
        stat(stats, null, "Recharge", trim(template.getRecharge()) + "s");
        stat(stats, null, "Level", owns(name) ? level(name) + " / " + MAX_PLANT_LEVEL : "-");
        detail.add(stats).width(PAGE_WIDTH).padBottom(6f).row();

        if (owns(name)) {
            detail.add(packetMeter(name)).width(PAGE_WIDTH).padBottom(8f).row();
        }
        detail.add(plantAction(template)).width(PAGE_WIDTH).padBottom(8f).row();
        detail.add(flavour(name)).width(PAGE_WIDTH).row();
    }

    // The live view: the entity playing its resting animation on a patch of its own lawn.
    private Table stage(views.gdx.sprite.EntitySprite sprite) {
        return stage(sprite, null);
    }

    private Table stage(views.gdx.sprite.EntitySprite sprite,
                        java.util.Map<String, Boolean> parts) {
        LiveEntityActor live = new LiveEntityActor(turf());
        live.show(sprite, parts);

        Table frame = new Table();
        frame.setBackground(context.assets().solid(STAGE_BACK));
        frame.add(live).grow().pad(4f);
        return frame;
    }

    // The hats a zombie wears, as a libPVZ visibility map.
    //
    // The armour is the TEMPLATE's, because the almanac has no live zombie to read a health stack from --
    // and it has to be read from somewhere, because ZombieArmor1, ZombieArmor2 and ZombieArmor4 are one
    // animation with three hats in it. Left unset they all drew as the same bare zombie, which is what
    // made Conehead, Buckethead and Brick indistinguishable on this screen.
    private java.util.Map<String, Boolean> armorParts(String alias,
                                                      views.gdx.sprite.EntitySprite sprite) {
        ZombieTemplate template = ZombieRegistry.getInstance().getZombieTemplateByAlias(alias);
        return template == null
                ? null
                : views.gdx.sprite.ArmorVisibility.forArmorTypes(template.getArmors(), sprite);
    }

    // A scrap of lawn for the live view to stand on.
    //
    // The dump has no lawn TILE -- the four lawns are single full-scene paintings -- but it does ship
    // this: the grass-and-dirt patch the game throws down where a plant is placed. It is exactly one
    // plant's worth of ground, which is precisely what a single entity on a page needs.
    //
    // (Cropping a square out of a world painting was the first attempt and it is a bad idea: which
    // part of a 1975x768 scene is open ground varies per world, so the crop was sky in one and a fence
    // in another.)
    private static final String TURF = "IMAGE_EFFECTS_DIRT_SPAWN_GRASS_DIRT_SPAWN_GRASS_179X50";

    private TextureRegion turf() {
        return region(TURF);
    }

    // Seed-packet progress as a bar rather than a fraction: five is a small enough number that a bar
    // reads at a glance, and "how close am I to upgrading" is the only question the number answers.
    private Table packetMeter(String name) {
        int held = packets(name);
        int need = Constants.UPGRADE_PLANT_REQUIRED_SEED_PACKETS;

        Table row = new Table();
        Drawable packet = MenuStyles.drawable(skin, UiArt.SEED_PACKET);
        if (packet != null) {
            row.add(new Image(packet)).size(22f, 28f).padRight(8f);
        }
        ProgressBar bar = new ProgressBar(0f, need, 1f, false, skin);
        bar.setValue(Math.min(held, need));
        bar.setAnimateDuration(0.25f);
        row.add(bar).growX().height(18f).padRight(8f);

        Label count = MenuStyles.label(skin, held + " / " + need, MenuStyles.TEXT);
        count.setFontScale(0.78f);
        count.setColor(held >= need ? READY : DIM);
        row.add(count).width(70f).right();
        return row;
    }

    // The almanac's own description line. plants.json and zombies.json carry no flavour text, so this
    // is the game's own words about the entity where it has any and a plain statement of fact where it
    // does not -- rather than lorem ipsum invented here.
    private Label flavour(String name) {
        Label label = MenuStyles.label(skin, owns(name)
                ? "In your seed bank and ready to plant."
                : "Not yet in your collection.", MenuStyles.TEXT);
        label.setWrap(true);
        label.setAlignment(Align.center);
        label.setColor(DIM);
        label.setFontScale(0.78f);
        return label;
    }

    // Buy it, level it up, or say why neither is on offer. The price labels are the model's own
    // constants, so a change to either shows here without this screen being told.
    private Table plantAction(PlantTemplate template) {
        String name = template.getName();
        Table box = new Table();
        if (!owns(name)) {
            box.add(priceLine(Constants.NEW_PLANT_COST_COINS + " coins")).padBottom(6f).row();
            box.add(action("Unlock", MenuStyles.BUTTON_GREEN,
                    "menu collection purchase-plant -p " + name)).width(260f).height(56f);
            return box;
        }
        if (level(name) >= MAX_PLANT_LEVEL) {
            box.add(hint("Maxed out. It cannot get any meaner."));
            return box;
        }
        box.add(priceLine(Constants.UPGRADE_PLANT_COST_COINS + " coins  +  "
                + Constants.UPGRADE_PLANT_REQUIRED_SEED_PACKETS + " packets")).padBottom(6f).row();
        box.add(action("Upgrade", MenuStyles.BUTTON_GREEN,
                "menu collection upgrade-plant -p " + name)).width(260f).height(56f);
        return box;
    }

    // Every refusal -- not enough coins, not enough packets, already maxed -- is the model's sentence,
    // arriving as a toast through GdxCollectionMenuRenderer. This screen does not pre-judge any of it;
    // it just rebuilds afterwards so the numbers it shows are the ones that came back.
    private TextButton action(String text, String style, String command) {
        TextButton button = MenuStyles.button(skin, text, style);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                commands.submit(command);
                rebuild();
            }
        });
        return button;
    }

    // ---- zombies ------------------------------------------------------------------------------

    private void buildZombieGrid() {
        int shown = 0;
        for (String alias : zombiesInOrder()) {
            grid.add(zombieCard(alias))
                    .size(EntityCardActor.CARD_WIDTH, EntityCardActor.CARD_HEIGHT).pad(5f);
            if (++shown % COLUMNS == 0) {
                grid.row();
            }
        }
        if (shown == 0) {
            grid.add(MenuStyles.label(skin, "The horde is unaccounted for.", MenuStyles.TEXT)).pad(40f);
        }
    }

    private List<String> zombiesInOrder() {
        List<String> aliases =
                new ArrayList<>(ZombieRegistry.getInstance().getZombieTemplatesByAlias().keySet());
        aliases.sort(String.CASE_INSENSITIVE_ORDER);
        return aliases;
    }

    private EntityCardActor zombieCard(String alias) {
        boolean seen = hasSeen(alias);
        views.gdx.sprite.EntitySprite sprite = context.sprites().get(alias);
        EntityIcon icon = new EntityIcon(sprite).showing(armorParts(alias, sprite));
        if (!seen) {
            icon.tinted(SILHOUETTE);
        }
        EntityCardActor card = new EntityCardActor(context.assets(), skin, art(icon),
                seen ? null : mysteryMark(),
                seen ? EntityNames.zombie(alias) : "", "", !seen);
        card.setSelected(alias.equalsIgnoreCase(selectedZombie));
        card.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (alias.equalsIgnoreCase(selectedZombie)) {
                    return;
                }
                selectedZombie = alias;
                rebuild();
                playDetailEntrance();
            }
        });
        return card;
    }

    private void buildZombieDetail() {
        if (selectedZombie == null) {
            detail.add(hint("Pick a zombie to read its page.")).width(PAGE_WIDTH).pad(20f);
            return;
        }
        ZombieTemplate template =
                ZombieRegistry.getInstance().getZombieTemplateByAlias(selectedZombie);
        boolean seen = hasSeen(selectedZombie);

        if (seen) {
            views.gdx.sprite.EntitySprite sprite = context.sprites().get(selectedZombie);
            detail.add(stage(sprite, armorParts(selectedZombie, sprite)))
                    .size(PAGE_WIDTH, STAGE_HEIGHT).padBottom(8f).row();
        }
        detail.add(MenuStyles.label(skin, seen ? EntityNames.zombie(selectedZombie) : "? ? ?",
                MenuStyles.HEADING)).width(PAGE_WIDTH).padBottom(2f).row();
        if (!seen || template == null) {
            detail.add(subtitle("Not yet encountered.")).width(PAGE_WIDTH).padBottom(10f).row();
            detail.add(hint("Survive one and its page fills itself in."))
                    .width(PAGE_WIDTH).pad(10f);
            return;
        }
        // No subtitle here, deliberately. The obvious candidate is objclass, and it is either the
        // alias again ("ZombieGargantuar") or a data artefact ("ZombiePropertySheet") -- neither of
        // which tells the player anything the stats below do not.
        detail.add().height(6f).row();

        Table stats = new Table();
        stat(stats, UiArt.METER_HEAD, "Health", String.valueOf(template.getBaseHp()));
        stat(stats, null, "Speed", trim(template.getSpeed()));
        stat(stats, null, "Bite damage", template.getEatDps() + "/s");
        stat(stats, null, "Wave cost", String.valueOf(template.getWavePointCost()));
        stat(stats, null, "Armour", armour(template));
        stat(stats, null, "Drops food", template.isCanSpawnPlantFood() ? "Yes" : "No");
        detail.add(stats).width(PAGE_WIDTH).padBottom(8f).row();

        Label note = MenuStyles.label(skin, "Logged in your almanac.", MenuStyles.TEXT);
        note.setWrap(true);
        note.setAlignment(Align.center);
        note.setColor(DIM);
        note.setFontScale(0.78f);
        detail.add(note).width(PAGE_WIDTH).row();
    }

    private String armour(ZombieTemplate template) {
        if (template.getArmors() == null || template.getArmors().isEmpty()) {
            return "None";
        }
        StringBuilder out = new StringBuilder();
        for (models.entities.zombies.Components.ArmorType armor : template.getArmors()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(pretty(armor.name()));
        }
        return out.toString();
    }

    // ---- shared -------------------------------------------------------------------------------

    // One stat row: icon, caption, value.
    //
    // iconId may be null, and for most rows it is. The dump's UI set has a sun and a zombie head and
    // no heart, sword or hourglass -- so rather than draw my own, the rows without art keep the icon
    // column's width and leave it empty. Everything still lines up, and nothing on screen is invented.
    private void stat(Table table, String iconId, String caption, String value) {
        Drawable icon = iconId == null ? null : MenuStyles.drawable(skin, iconId);
        if (icon != null) {
            table.add(new Image(icon)).size(ICON_SIZE).padRight(6f).padBottom(3f);
        } else {
            table.add().size(ICON_SIZE).padRight(6f).padBottom(3f);
        }

        Label name = MenuStyles.label(skin, caption, MenuStyles.TEXT);
        name.setAlignment(Align.left);
        name.setColor(DIM);
        name.setFontScale(0.8f);

        Label read = MenuStyles.label(skin, value, MenuStyles.TEXT);
        read.setAlignment(Align.right);
        read.setWrap(true);

        table.add(name).width(110f).left().padBottom(3f);
        table.add(read).width(PAGE_WIDTH - 110f - ICON_SIZE - 12f).right().padBottom(3f).row();
    }

    private Label subtitle(String text) {
        Label label = MenuStyles.label(skin, text, MenuStyles.TEXT);
        label.setColor(DIM);
        label.setFontScale(0.8f);
        return label;
    }

    private Label hint(String text) {
        Label label = MenuStyles.label(skin, text, MenuStyles.TEXT);
        label.setColor(DIM);
        label.setWrap(true);
        label.setAlignment(Align.center);
        return label;
    }

    // The icon, or a stand-in when the dump has no animation for this entity.
    //
    // Rotobaga, Cat-tail, Sun-shroom and a few others simply are not in pvz-assets, and EntitySprite's
    // still-image fallback has no still to fall back to. An empty card reads as a broken screen; a
    // question mark reads as "we do not have a picture of this one", which is the truth.
    private Actor art(EntityIcon icon) {
        if (icon.hasArt()) {
            return icon;
        }
        Label mark = MenuStyles.label(skin, "?", MenuStyles.TITLE);
        mark.setAlignment(Align.center);
        mark.setColor(LOCKED_ART);
        return mark;
    }

    private Label priceLine(String text) {
        Label label = MenuStyles.label(skin, text, MenuStyles.TEXT);
        label.setColor(COST);
        return label;
    }

    @Override
    protected void refresh() {
        Profile profile = profile();
        coins.setText(profile == null ? "0" : String.valueOf(profile.getCoins()));
        gems.setText(profile == null ? "0" : String.valueOf(profile.getGems()));
    }

    // ---- model reads --------------------------------------------------------------------------
    //
    // Every one of these compares case-insensitively. The Profile stores plant names lower-cased while
    // the templates carry the display name ("Wall-nut"), so a direct contains() on either side is
    // right about half the time and silently wrong the rest.

    private boolean owns(String plantName) {
        Profile profile = profile();
        if (profile == null || profile.getUnlockedPlants() == null) {
            return false;
        }
        for (String owned : profile.getUnlockedPlants()) {
            if (owned != null && owned.equalsIgnoreCase(plantName)) {
                return true;
            }
        }
        return false;
    }

    private int level(String plantName) {
        Profile profile = profile();
        return profile == null ? 1
                : lookup(profile.getPlantsLevels(), plantName, 1);
    }

    private int packets(String plantName) {
        Profile profile = profile();
        return profile == null ? 0
                : lookup(profile.getOwnedSeedPackets(), plantName, 0);
    }

    private static int lookup(java.util.Map<String, Integer> map, String key, int fallback) {
        if (map == null || key == null) {
            return fallback;
        }
        for (java.util.Map.Entry<String, Integer> entry : map.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue() == null ? fallback : entry.getValue();
            }
        }
        return fallback;
    }

    private boolean upgradable(String plantName) {
        return owns(plantName)
                && level(plantName) < MAX_PLANT_LEVEL
                && packets(plantName) >= Constants.UPGRADE_PLANT_REQUIRED_SEED_PACKETS;
    }

    private boolean hasSeen(String alias) {
        Profile profile = profile();
        if (profile == null || profile.getSeenZombieAliases() == null) {
            return false;
        }
        for (String seen : profile.getSeenZombieAliases()) {
            if (seen != null && seen.equalsIgnoreCase(alias)) {
                return true;
            }
        }
        return false;
    }

    private Profile profile() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getProfile();
    }

    // ---- text ---------------------------------------------------------------------------------

    // Turns a data key into something readable: SUN_PRODUCER becomes "Sun Producer", and
    // ZombieBeachOctopus becomes "Beach Octopus". Both shapes appear -- categories and armour are
    // SCREAMING_SNAKE, zombie aliases are CamelCase with a "Zombie" prefix nobody needs to read on a
    // screen that is entirely zombies.
    private static String pretty(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String text = key.trim();
        if (text.length() > 6 && text.startsWith("Zombie") && Character.isUpperCase(text.charAt(6))) {
            text = text.substring(6);
        }
        text = text.replace('_', ' ').replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        StringBuilder out = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    // Drops a trailing ".0" so 2.0 reads as 2 and 1.5 still reads as 1.5.
    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
