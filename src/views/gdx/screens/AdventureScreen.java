package views.gdx.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import models.game.Chapter;
import models.game.EnvironmentType;
import models.game.Level;
import models.user.Profile;
import models.user.User;
import views.gdx.core.GdxContext;
import views.gdx.ui.ButtonJuice;
import views.gdx.ui.MenuStyles;

// The world map: which world to play, and which level inside it.
//
// Both halves still go through the commands the terminal takes. Chapters are selected by NUMBER --
// "menu enter chapter -c 2" -- because the command grammar splits on whitespace and every chapter is
// called something like "Ancient Egypt". Nothing about the presentation below changes that.
//
// There is no dedicated world-card or level-node art in the dump; the WORLDMAP family is thousands of
// animation frames for a scrolling map this build does not have. What there IS, already loaded, is each
// world's own lawn painting -- which is the most recognisable image of a world there could be. So a
// world card is that world's lawn, and the selected one also becomes the screen's backdrop.
public final class AdventureScreen extends MenuScreen {

    private static final String ICON_LOCK = "image_ui_cards_lock_medium";
    private static final String ICON_STAR = "image_ui_generic_star_icon";

    private static final float CARD_WIDTH = 250f;
    private static final float CARD_HEIGHT = 150f;
    // Big enough for an island to read as an island. A 74px stone was fine for a coloured disc and far
    // too small for a painted map tile.
    private static final float NODE_WIDTH = 216f;
    private static final float ISLAND_HEIGHT = 176f;

    // The number medallion at the castle's base. Round and small -- it labels the node, it is not the
    // node.
    private static final float BADGE_SIZE = 52f;
    private static final float BASE_OVERLAP = -14f;

    private static final float NODE_HEIGHT = 42f + ISLAND_HEIGHT + BADGE_SIZE + BASE_OVERLAP;

    // Card framing and focus.
    private static final float CARD_BORDER = 5f;
    private static final float UNSELECTED_SCALE = 0.85f;
    private static final float UNSELECTED_ALPHA = 0.6f;

    // How far alternate nodes drop, so the row reads as a path rather than a row of buttons.
    private static final float ZIGZAG = 26f;

    private static final Color CARD_SHADE = new Color(0f, 0f, 0f, 0.42f);
    private static final Color PATH_SCRIM = new Color(0f, 0f, 0f, 0.34f);
    // What a locked island is dimmed to, so it reads as unreachable without being invisible.
    private static final Color ISLAND_LOCKED = new Color(0.42f, 0.44f, 0.52f, 1f);
    // Stone, not a primary. A saturated badge beside painted castles reads as a debug overlay.
    private static final Color BADGE_STONE = new Color(0.16f, 0.15f, 0.18f, 0.88f);
    private static final Color BADGE_STONE_LOCKED = new Color(0.16f, 0.15f, 0.18f, 0.55f);
    // Behind card captions, so type stays legible over any of the four paintings.
    private static final Color TEXT_PLATE = new Color(0f, 0f, 0f, 0.5f);

    private static final Color NODE_DONE = new Color(0.35f, 0.78f, 0.30f, 0.95f);
    private static final Color NODE_OPEN = new Color(0.95f, 0.72f, 0.20f, 0.95f);
    private static final Color NODE_LOCKED = new Color(0.30f, 0.29f, 0.27f, 0.9f);
    private static final Color DIM = new Color(0.72f, 0.70f, 0.66f, 1f);

    private Table worldRow;
    private ScrollPane carousel;
    private Table levelPath;
    private Label chapterTitle;
    private Image worldBackdrop;

    public AdventureScreen(GdxContext context) {
        super(context);
    }

    @Override
    protected void build(Table root) {
        // The selected world's lawn, full bleed, between the title painting and the UI. Updated when the
        // world changes, which is what makes picking one feel like travelling to it.
        worldBackdrop = new Image();
        worldBackdrop.setScaling(Scaling.fill);
        worldBackdrop.setFillParent(true);
        // Opaque. "One background per world" means exactly one: at 0.55 the title painting showed
        // through and the two fought each other in the middle of the screen, which is where the level
        // path sits.
        worldBackdrop.getColor().a = 1f;
        ambient.addActor(worldBackdrop);

        root.setFillParent(true);
        root.top().pad(18f);

        root.add(MenuStyles.title(skin, "Adventure")).padBottom(12f).row();
        // Tall enough for the selected card at full scale plus its frame, or the focused one is clipped
        // by the pane it sits in.
        root.add(worldCarousel()).width(1180f).height(CARD_HEIGHT + 40f).padBottom(22f).row();

        chapterTitle = MenuStyles.label(skin, "", MenuStyles.HEADING);
        root.add(chapterTitle).padBottom(16f).row();

        // A soft strip behind the path. The lawn painting is busiest exactly where the nodes sit, and
        // painted islands on painted flagstone need something to separate them.
        Stack pathBand = new Stack();
        pathBand.add(new Image(context.assets().solid(PATH_SCRIM)));
        levelPath = new Table();
        pathBand.add(levelPath);
        // expandY with centre, not top: the path is the subject of this screen and belongs in the
        // middle of the space between the carousel and the bar, not pinned under the world cards with
        // a screen's worth of empty lawn beneath it.
        root.add(pathBand).growX().expandY().center().row();

        root.add(bottomBar()).padTop(6f).row();

        rebuild();
    }

    // A horizontal ScrollPane of world cards.
    //
    // Scroll is forced on the X axis only and fling is left on, so a drag throws the row the way a map
    // should move. Vertical scroll is disabled or the row fights the page for the wheel.
    private ScrollPane worldCarousel() {
        worldRow = new Table();
        // Room for the selected card to grow into without touching its neighbours.
        worldRow.defaults().space(22f);

        ScrollPane pane = new ScrollPane(worldRow, skin);
        carousel = pane;
        pane.setScrollingDisabled(false, true);
        pane.setFadeScrollBars(true);
        pane.setOverscroll(false, false);
        // Otherwise the pane keeps keyboard focus and swallows the Escape the screen binds to Back.
        pane.setScrollBarPositions(false, true);
        return pane;
    }

    // One card per unlocked chapter: the world's own lawn, its name, and how far through it the player
    // is. Locked worlds are not in the profile at all, so there is nothing to grey out -- but the card
    // still carries the padlock branch for when a future phase lists them.
    private void rebuildWorlds() {
        worldRow.clear();
        java.util.List<Chapter> chapters = chapters();
        if (chapters.isEmpty()) {
            worldRow.add(MenuStyles.label(skin, "No worlds unlocked yet.", MenuStyles.TEXT));
            return;
        }
        Chapter open = currentChapter();
        for (int i = 0; i < chapters.size(); i++) {
            Chapter chapter = chapters.get(i);
            boolean selected = open != null && open.getName() != null
                    && open.getName().equals(chapter.getName());
            Table card = worldCard(chapter, i + 1, selected);
            worldRow.add(card).size(CARD_WIDTH, CARD_HEIGHT);
            if (selected) {
                focused = card;
            }
        }
        // Bring the selected world into the middle of the pane. Deferred: the row has no laid-out
        // coordinates until Scene2D's next layout pass, so scrolling now would scroll to (0, 0).
        snapToFocused = true;
    }

    private Table focused;
    private boolean snapToFocused;

    // Centres the pane on the selected card, once the layout that gives it a position has run.
    private void snapCarousel() {
        if (!snapToFocused || focused == null || carousel == null) {
            return;
        }
        snapToFocused = false;
        carousel.validate();
        carousel.scrollTo(focused.getX(), focused.getY(), focused.getWidth(), focused.getHeight(),
                true, false);
    }

    private Table worldCard(Chapter chapter, int number, boolean selected) {
        Stack inner = new Stack();

        Image art = new Image(worldArt(chapter));
        art.setScaling(Scaling.fill);
        inner.add(art);
        inner.add(new Image(context.assets().solid(CARD_SHADE)));

        // Text plates. The world paintings are bright and busy at the top AND bottom, so a caption laid
        // straight over one is unreadable on at least one of the four. A dark strip behind each line
        // costs nothing and makes the type legible on every world regardless of what is behind it.
        Table overlay = new Table();
        overlay.add(plated(prettyName(chapter.getName()), MenuStyles.HEADING, null))
                .growX().top().row();
        overlay.add().expand().row();
        overlay.add(plated(progressText(chapter), MenuStyles.TEXT, progressTint(chapter)))
                .growX().bottom().row();
        inner.add(overlay);

        // The frame: a dark border drawn as padding around the art, so a card reads as a card rather
        // than as a photograph butted against its neighbour.
        Table card = new Table();
        card.setBackground(MenuStyles.panelFill(skin));
        card.pad(CARD_BORDER);
        card.add(inner).grow();

        // Focus. The selected world is full size and fully lit; the rest step back, which is what turns
        // a row of four equal rectangles into a carousel with a subject.
        card.setTransform(true);
        card.setOrigin(Align.center);
        card.setScale(selected ? 1f : UNSELECTED_SCALE);
        card.getColor().a = selected ? 1f : UNSELECTED_ALPHA;
        ButtonJuice.applyTo(card);
        card.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                commands.submit("menu enter chapter -c " + number);
                rebuild();
            }
        });
        return card;
    }

    // A caption on its own dark plate, so it stays readable over any of the four paintings.
    private Table plated(String text, String style, Color tint) {
        Table plate = new Table();
        plate.setBackground(context.assets().solid(TEXT_PLATE));
        Label label = MenuStyles.label(skin, text, style);
        if (tint != null) {
            label.setColor(tint);
        }
        plate.add(label).pad(3f, 8f, 3f, 8f);
        return plate;
    }

    private int clearedCount(Chapter chapter) {
        int done = 0;
        if (chapter.getLevels() != null) {
            for (Level level : chapter.getLevels()) {
                if (level != null && level.isCompleted()) {
                    done++;
                }
            }
        }
        return done;
    }

    private String progressText(Chapter chapter) {
        int total = chapter.getLevels() == null ? 0 : chapter.getLevels().length;
        return total == 0 ? "" : clearedCount(chapter) + "/" + total + " levels done";
    }

    private Color progressTint(Chapter chapter) {
        int total = chapter.getLevels() == null ? 0 : chapter.getLevels().length;
        return total > 0 && clearedCount(chapter) >= total ? NODE_DONE : DIM;
    }

    // The open world's levels, as stepping stones rather than a grid of squares.
    private void rebuildLevels() {
        levelPath.clear();
        Chapter chapter = currentChapter();
        if (chapter == null || chapter.getLevels() == null) {
            chapterTitle.setText("Pick a world to see its levels.");
            return;
        }
        chapterTitle.setText(prettyName(chapter.getName()));

        Level[] levels = chapter.getLevels();
        for (int i = 0; i < levels.length; i++) {
            // Alternating top padding is the whole zigzag. An absolutely positioned WidgetGroup would
            // give a prettier curve, but it would also have to be re-laid-out by hand on every resize;
            // the Table already handles that and the stagger reads the same.
            float drop = i % 2 == 0 ? 0f : ZIGZAG;
            levelPath.add(levelNode(levels[i], i + 1, levels.length, isNext(levels, i), chapter.getEnvironment()))
                    .size(NODE_WIDTH, NODE_HEIGHT).padTop(drop).padRight(4f).top();
        }
    }

    // The first level that is open but not yet cleared -- the one the player is meant to play next.
    private boolean isNext(Level[] levels, int index) {
        for (int i = 0; i < levels.length; i++) {
            Level level = levels[i];
            if (level != null && level.isUnlocked() && !level.isCompleted()) {
                return i == index;
            }
        }
        return false;
    }

    // A round stone: green with a star when cleared, gold when open, grey with a padlock when not.
    //
    // Left clickable even when locked, so the refusal comes from the model's own "this level is
    // unavailable!" rather than from a button that silently does nothing.
    private Table levelNode(Level level, int number, int total, boolean next, EnvironmentType environment) {
        boolean unlocked = level != null && level.isUnlocked();
        boolean cleared = level != null && level.isCompleted();

        // Island on top, coloured plinth beneath it carrying the number.
        //
        // The disc used to sit BEHIND the island, which put the star and the number on top of the
        // artwork and made the node read as cluttered. As a base it does two jobs instead: it gives the
        // state its colour somewhere clean, and it gives a cut-out island something to stand on --
        // which is what stops a wispy one from looking like it is floating in nothing.
        Table node = new Table();

        // The star sits CENTRED ABOVE the island, in a row of its own, so it is anchored to the node
        // rather than floating wherever the art's bounding box happened to put it.
        Drawable badge = MenuStyles.drawable(skin, cleared ? ICON_STAR : unlocked ? null : ICON_LOCK);
        Table crown = new Table();
        if (badge != null) {
            Image mark = new Image(badge);
            mark.setScaling(Scaling.fit);
            crown.add(mark).size(40f);
        }
        node.add(crown).height(42f).center().row();

        TextureRegion island = region(levelIslandId(environment, number, total));
        if (island != null) {
            Image art = new Image(new TextureRegionDrawable(island));
            art.setScaling(Scaling.fit);
            if (!unlocked && !cleared) {
                art.setColor(ISLAND_LOCKED);
            }
            node.add(art).size(NODE_WIDTH, ISLAND_HEIGHT).row();
        } else {
            node.add().size(NODE_WIDTH, ISLAND_HEIGHT).row();
        }

        // A small dark medallion at the castle's base, not the flat green oval that was here before.
        //
        // The oval was programmer art: a saturated primary colour next to detailed painted castles, and
        // it read as a debug overlay. State is carried by the star above and by the island's own dim
        // instead, so the badge only has to say WHICH level this is -- which a quiet stone-coloured
        // disc does without competing with the artwork.
        Stack medallion = new Stack();
        medallion.add(new Image(context.assets().round(
                unlocked || cleared ? BADGE_STONE : BADGE_STONE_LOCKED)));
        Table caption = new Table();
        Label numberLabel = MenuStyles.label(skin, String.valueOf(number), MenuStyles.HEADING);
        numberLabel.setColor(unlocked || cleared ? Color.WHITE : DIM);
        caption.add(numberLabel);
        medallion.add(caption);
        node.add(medallion).size(BADGE_SIZE, BADGE_SIZE).padTop(BASE_OVERLAP);

        node.setTransform(true);
        node.setOrigin(Align.center);
        ButtonJuice.applyTo(node);
        if (next) {
            // The one node that asks to be pressed. A slow breath rather than a flash: it has to catch
            // the eye without competing with the world art behind it.
            node.addAction(Actions.forever(Actions.sequence(
                    Actions.scaleTo(1.12f, 1.12f, 0.6f, Interpolation.sine),
                    Actions.scaleTo(1f, 1f, 0.6f, Interpolation.sine))));
        }
        node.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                commands.submit("level -l " + number);
            }
        });
        return node;
    }

    // Everywhere else the play menu leads, in one bar along the bottom.
    private Table bottomBar() {
        Table bar = new Table();
        bar.setBackground(MenuStyles.panelFill(skin));
        bar.pad(10f, 16f, 10f, 16f);
        bar.defaults().width(178f).height(46f).padRight(8f);

        bar.add(navButton("Greenhouse", "menu greenhouse", MenuStyles.BUTTON_BROWN));
        bar.add(navButton("Travel Log", "menu travel-log", MenuStyles.BUTTON_BROWN));
        bar.add(navButton("Leaderboard", "menu leaderboard", MenuStyles.BUTTON_BROWN));
        bar.add(navButton("Scoring Game", "menu scoring-game", MenuStyles.BUTTON_GREEN));

        TextButton back = MenuStyles.button(skin, "Back", MenuStyles.BUTTON_PURPLE);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                goBack();
            }
        });
        bar.add(back).padRight(0f);
        return bar;
    }

    private TextButton navButton(String text, String command, String style) {
        TextButton button = MenuStyles.button(skin, text, style);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                commands.submit(command);
            }
        });
        return button;
    }

    private void rebuild() {
        rebuildWorlds();
        rebuildLevels();
        updateBackdrop();
    }

    private void updateBackdrop() {
        Chapter chapter = currentChapter();
        TextureRegion art = chapter == null ? null : worldRegion(chapter.getEnvironment());
        worldBackdrop.setDrawable(art == null ? null : new TextureRegionDrawable(art));
    }

    private Drawable worldArt(Chapter chapter) {
        TextureRegion art = worldRegion(chapter.getEnvironment());
        return art == null ? context.assets().solid(NODE_LOCKED) : new TextureRegionDrawable(art);
    }

    // One image per world, from that world's own WorldMap atlas.
    //
    // These atlases hold the floating islands the real game's map is assembled from -- there is no
    // single full-map background in the dump -- so each world is represented by its largest, most
    // characteristic island: Egypt's excavated sandstone, the ice cavern, the beach cove, the dark
    // keep. The same image is the world's card AND the screen's backdrop, which is what makes picking
    // a world feel like arriving in it.
    //
    // Audition alternatives with -Dpvz.probeRegions=IMAGE_WORLDMAP_EGYPT_ISLAND22,... -- every world
    // has two or three islands of a similar size and which one reads best is a matter of taste.
    // The islands each world's levels stand on, largest first.
    //
    // Chosen by size out of that world's WorldMap atlas -- the big ones are the themed set pieces
    // (Egypt's excavated dig, the ice cavern) and the small ones are scenery rubble. Levels index into
    // this and wrap, so a chapter of any length gets islands and level 1 always gets the grandest.
    //
    // Audition alternatives with -Dpvz.probeRegions=IMAGE_WORLDMAP_EGYPT_ISLAND22,...
    // Three islands per world: the opening one, the one the middle levels share, and the Zomboss node
    // that marks the finale. Picked by eye rather than by bounding-box size, which is what my first
    // pass did -- and size turned out to select mist and cloud sprites, because a wisp has enormous
    // bounds and almost no substance.
    //
    // Dark Ages uses its Zomboss node throughout; its ordinary islands are the wisps.
    private String[] worldIslands(EnvironmentType environment) {
        return switch (environment == null ? EnvironmentType.ANCIENT_EGYPT : environment) {
            case ANCIENT_EGYPT -> new String[] {
                "IMAGE_WORLDMAP_EGYPT_ISLAND1",
                "IMAGE_WORLDMAP_EGYPT_ISLAND3",
                "IMAGE_WORLDMAP_ZOMBOSS_NODE_EGYPT_ZOMBOSS_NODE_EGYPT_914X994"};
            case FROSTBITE_CAVES -> new String[] {
                "IMAGE_WORLDMAP_ICEAGE_ISLAND1",
                "IMAGE_WORLDMAP_ICEAGE_ANIM3_ANIM3_1307X1318",
                "IMAGE_WORLDMAP_ZOMBOSS_NODE_ICEAGE_ZOMBOSS_NODE_ICEAGE_1055X1280"};
            case BIG_WAVE_BEACH -> new String[] {
                "IMAGE_WORLDMAP_BEACH_ISLAND1",
                "IMAGE_WORLDMAP_BEACH_ISLAND24",
                "IMAGE_WORLDMAP_ZOMBOSS_NODE_BEACH_ZOMBOSS_NODE_BEACH_905X1096"};
            case DARK_AGES -> new String[] {
                "IMAGE_WORLDMAP_ZOMBOSS_NODE_DARK_ZOMBOSS_NODE_DARK_905X1096",
                "IMAGE_WORLDMAP_ZOMBOSS_NODE_DARK_ZOMBOSS_NODE_DARK_905X1096",
                "IMAGE_WORLDMAP_ZOMBOSS_NODE_DARK_ZOMBOSS_NODE_DARK_905X1096"};
        };
    }

    // First level opens, last level is the Zomboss node, everything between shares the middle island.
    // Keyed off the chapter's real length rather than a hardcoded 4, so a chapter of any size still
    // gets its finale on the last node.
    private String levelIslandId(EnvironmentType environment, int number, int total) {
        String[] islands = worldIslands(environment);
        if (number <= 1) {
            return islands[0];
        }
        return number >= total ? islands[2] : islands[1];
    }

    // Our own painted world backdrops, one per world, dropped into assets/worlds/.
    //
    // These are not part of the PopCap dump -- the dump has no per-world menu background, which is what
    // sent the first two attempts here through the lawn textures and the WorldMap islands. They load
    // through Assets.ownArt (plain files, not RESOURCES.json), and a missing one silently falls back to
    // the lawn painting, so the screen works whether or not they are present.
    // Both extensions are tried rather than one being mandated: which a painting is saved as is a
    // detail of how it was exported, and a background silently not appearing because it was a .jpg is
    // an annoying way to spend an afternoon.
    private static final String[] ART_EXTENSIONS = {".jpg", ".png"};

    private TextureRegion worldBackground(EnvironmentType environment) {
        String name = switch (environment == null ? EnvironmentType.ANCIENT_EGYPT : environment) {
            case ANCIENT_EGYPT -> "egypt";
            case FROSTBITE_CAVES -> "iceage";
            case BIG_WAVE_BEACH -> "beach";
            case DARK_AGES -> "dark";
        };
        for (String extension : ART_EXTENSIONS) {
            TextureRegion art = context.assets().ownArt("assets/worlds/" + name + extension);
            if (art != null) {
                return art;
            }
        }
        return null;
    }

    // The lawn painting, used only when the painted backdrop above is missing.
    //
    // The WorldMap atlases were the obvious place to look and they do not contain what is needed: they
    // hold the floating islands the real game's map is ASSEMBLED from, cut-outs of 890x274 or so with
    // transparent surrounds. Stretched to fill a 1280x720 screen every one of them turns into an
    // unrecognisable blur, and four of them side by side as cards are indistinguishable from each
    // other. The lawn painting is a full scene, is already loaded, and actually looks like its world.
    //
    // worldImageId is kept because the islands are the right art for map FURNITURE -- a scrolling map
    // with islands as level nodes is the shape this would take if it grew -- just not for a backdrop.
    private TextureRegion worldRegion(EnvironmentType environment) {
        TextureRegion painted = worldBackground(environment);
        if (painted != null) {
            return painted;
        }
        String key = switch (environment == null ? EnvironmentType.ANCIENT_EGYPT : environment) {
            case ANCIENT_EGYPT -> "EGYPT";
            case FROSTBITE_CAVES -> "ICEAGE";
            case BIG_WAVE_BEACH -> "BEACH";
            case DARK_AGES -> "DARK";
        };
        return region("IMAGE_BACKGROUNDS_" + key + "_TEXTURE");
    }

    // Chapters are keyed as ANCIENT_EGYPT, which is a data key and not a title. Purely presentational
    // -- the key is what still goes back to the model.
    private static String prettyName(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String word : key.trim().split("[_\\s]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return out.toString();
    }

    private java.util.List<Chapter> chapters() {
        Profile profile = profile();
        if (profile == null || profile.getUnlockedChapters() == null) {
            return java.util.List.of();
        }
        return profile.getUnlockedChapters();
    }

    private Chapter currentChapter() {
        Profile profile = profile();
        return profile == null ? null : profile.getCurrentChapter();
    }

    private Profile profile() {
        User user = context.appSession().getCurrentUser();
        return user == null ? null : user.getProfile();
    }
}
