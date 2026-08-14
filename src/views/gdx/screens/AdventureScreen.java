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
    private static final float SELECTED_SCALE = 1.1f;
    private static final float UNSELECTED_SCALE = 0.9f;
    private static final float UNSELECTED_ALPHA = 0.6f;
    // Hover is a multiplier on the card's resting scale, not an absolute -- so hovering a selected card
    // lifts it from 1.1 rather than shrinking it to a button's 1.05.
    private static final float HOVER_LIFT = 1.06f;

    // The shelf the cards sit on: four cards, the gaps between them, the 10px the pane is inset by, and
    // the room an end card needs to grow into when hovered. Deliberately not wider -- at 1220 the shelf
    // ran to within 30px of both screen edges and read as a band across the whole screen rather than as
    // something sitting ON the world.
    private static final float CAROUSEL_WIDTH = 1130f;

    // How long a change of world takes. The cards resize into their new roles, the level path dissolves
    // and returns as the new world's, and the backdrop cross-fades under both -- the backdrop slowest,
    // because it is the largest thing moving and a fast dissolve on a full-screen painting reads as a
    // flicker.
    private static final float SELECT_SECONDS = 0.28f;
    private static final float SWAP_SECONDS = 0.14f;
    private static final float BACKDROP_SECONDS = 0.45f;
    private static final float NODE_FADE_SECONDS = 0.22f;
    private static final float NODE_STAGGER = 0.05f;

    // The dotted trail between level nodes.
    private static final float DOT_SIZE = 9f;
    private static final float DOT_SPACING = 26f;
    private static final Color DOT_TINT = new Color(1f, 0.95f, 0.75f, 0.5f);

    // How far alternate nodes drop, so the row reads as a path rather than a row of buttons.
    private static final float ZIGZAG = 26f;

    private static final Color CARD_SHADE = new Color(0f, 0f, 0f, 0.42f);
    private static final Color PATH_SCRIM = new Color(0f, 0f, 0f, 0.34f);
    // What a locked island is dimmed to, so it reads as unreachable without being invisible.
    private static final Color ISLAND_LOCKED = new Color(0.42f, 0.44f, 0.52f, 1f);
    // Stone, not a primary. A saturated badge beside painted castles reads as a debug overlay.
    // Near-opaque with a lighter rim behind it, so the number sits on a defined medallion rather than
    // on a faint smudge that the background colour shows straight through.
    private static final Color BADGE_STONE = new Color(0.13f, 0.12f, 0.14f, 0.96f);
    private static final Color BADGE_STONE_LOCKED = new Color(0.13f, 0.12f, 0.14f, 0.7f);
    private static final Color BADGE_RIM = new Color(0.85f, 0.78f, 0.55f, 0.75f);
    private static final Color BADGE_RIM_LOCKED = new Color(0.55f, 0.53f, 0.48f, 0.5f);
    // Behind card captions, so type stays legible over any of the four paintings.
    private static final Color TEXT_PLATE = new Color(0f, 0f, 0f, 0.5f);
    // Behind the whole carousel, so the cards read as one shelf.
    private static final Color CAROUSEL_PLATE = new Color(0f, 0f, 0f, 0.4f);

    private static final Color NODE_DONE = new Color(0.35f, 0.78f, 0.30f, 0.95f);
    private static final Color NODE_OPEN = new Color(0.95f, 0.72f, 0.20f, 0.95f);
    private static final Color NODE_LOCKED = new Color(0.30f, 0.29f, 0.27f, 0.9f);
    private static final Color DIM = new Color(0.72f, 0.70f, 0.66f, 1f);

    private Table worldRow;
    private ScrollPane carousel;
    private Table levelPath;
    private Label chapterTitle;
    private Image worldBackdrop;
    private Image worldBackdropOut;

    private final java.util.List<WorldCard> cards = new java.util.ArrayList<>();

    public AdventureScreen(GdxContext context) {
        super(context);
    }

    @Override
    protected void build(Table root) {
        // The selected world's painting, full bleed, between the title art and the UI. Updated when the
        // world changes, which is what makes picking one feel like travelling to it.
        //
        // TWO images, not one: a single Image cannot cross-fade with itself. The outgoing world holds
        // still on the upper layer while the new one is swapped in underneath it, and then dissolves --
        // so there is never a frame with no world behind the level path.
        //
        // Both opaque. "One background per world" means exactly one: at 0.55 the title painting showed
        // through and the two fought each other in the middle of the screen, which is where the level
        // path sits.
        worldBackdrop = fullBleed();
        worldBackdropOut = fullBleed();
        worldBackdropOut.getColor().a = 0f;
        ambient.addActor(worldBackdrop);
        ambient.addActor(worldBackdropOut);

        root.setFillParent(true);
        root.top().pad(18f);

        root.add(MenuStyles.title(skin, "Adventure")).padBottom(12f).row();
        // Tall enough for the selected card at full scale plus its frame, or the focused one is clipped
        // by the pane it sits in.
        // A dark plate behind the whole carousel, so the row of cards reads as one shelf sitting on the
        // world rather than as four images floating loose on the painting.
        Stack carouselBand = new Stack();
        carouselBand.add(new Image(context.assets().solid(CAROUSEL_PLATE)));
        Table carouselHolder = new Table();
        carouselHolder.add(worldCarousel()).grow().pad(10f);
        carouselBand.add(carouselHolder);
        root.add(carouselBand).width(CAROUSEL_WIDTH).height(CARD_HEIGHT + 56f).padBottom(22f).row();

        chapterTitle = MenuStyles.label(skin, "", MenuStyles.HEADING);
        root.add(chapterTitle).padBottom(16f).row();

        // A soft strip behind the path. The lawn painting is busiest exactly where the nodes sit, and
        // painted islands on painted flagstone need something to separate them.
        Stack pathBand = new Stack();
        pathBand.add(new Image(context.assets().solid(PATH_SCRIM)));
        levelPath = new LevelPath(context.assets().round(DOT_TINT));
        pathBand.add(levelPath);
        // expandY with centre, not top: the path is the subject of this screen and belongs in the
        // middle of the space between the carousel and the bar, not pinned under the world cards with
        // a screen's worth of empty lawn beneath it.
        root.add(pathBand).growX().expandY().center().row();

        root.add(bottomBar()).padTop(6f).row();

        rebuild();
    }

    private Image fullBleed() {
        Image image = new Image();
        image.setScaling(Scaling.fill);
        image.setFillParent(true);
        return image;
    }

    // A Table that keeps its origin at its own centre.
    //
    // Origin is measured in pixels, and a widget has no size until its first layout pass -- so
    // setOrigin(Align.center) at build time records (0, 0), and every scale animation afterwards grows
    // or shrinks the widget out of its bottom-left corner instead of about its middle. That is why the
    // unselected cards sat low in the shelf and the gaps between them widened across the row, and why
    // the pulsing "play me next" node drifted up and to the right as it breathed.
    //
    // Re-taking it on every layout also survives a resize, which setting it once after the first pass
    // would not.
    private static final class Centred extends Table {

        Centred() {
            setTransform(true);
        }

        @Override
        public void layout() {
            super.layout();
            setOrigin(Align.center);
        }
    }

    // A built card, kept so that changing world can be animated ONTO it rather than rebuilt around it.
    //
    // Rebuilding is what made picking a world snap: every card was discarded and a new one created
    // already at its new size, so there was nothing left to animate from.
    private static final class WorldCard {

        private final Table root;
        private final String chapterKey;

        private boolean selected;
        private boolean hovered;

        WorldCard(Table root, String chapterKey) {
            this.root = root;
            this.chapterKey = chapterKey;
        }

        // Where this card should be right now, from its state -- never a fixed number. Hover is a
        // multiplier on top of the resting size, so hovering a selected card lifts it from 1.1 instead
        // of dragging it down to a button's 1.05.
        float scale() {
            return (selected ? SELECTED_SCALE : UNSELECTED_SCALE) * (hovered ? HOVER_LIFT : 1f);
        }

        float alpha() {
            return selected ? 1f : UNSELECTED_ALPHA;
        }
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
        // clearChildren, never clear: Group.clear() is Actor.clear() plus the children, and Actor.clear
        // throws away the actor's own ACTIONS and listeners too. See rebuildLevels, where that quietly
        // deleted the fade that was in the middle of calling it.
        worldRow.clearChildren();
        cards.clear();
        java.util.List<Chapter> chapters = chapters();
        if (chapters.isEmpty()) {
            worldRow.add(MenuStyles.label(skin, "No worlds unlocked yet.", MenuStyles.TEXT));
            return;
        }
        for (int i = 0; i < chapters.size(); i++) {
            WorldCard card = worldCard(chapters.get(i), i + 1);
            cards.add(card);
            worldRow.add(card.root).size(CARD_WIDTH, CARD_HEIGHT);
        }
        applySelection(false);
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

    // The layout that gives the cards positions does not exist until the frame after they are built, so
    // the snap is asked for here rather than at the point the selection changes.
    @Override
    protected void refresh() {
        snapCarousel();
        runWorldCheck();
    }

    // Proves that picking a world actually MOVES the cards, since a screenshot run has no mouse.
    //
    // -Dpvz.worldCheck=<chapter number> clicks that world's card once the screen has finished arriving,
    // and logs every card's scale and alpha before and after. Driven through the Stage as a real touch
    // rather than by calling selectWorld() directly, so what is being checked is the wired path -- "the
    // listener is attached" and "the listener changes anything" are different claims.
    // Late enough that the screen's own 0.3s entrance has finished, so the two animations are not read
    // as one, and long enough after the click for the 0.28s selection tween to have settled.
    private static final int WORLD_CHECK_FRAME = 30;
    private static final int WORLD_CHECK_SETTLE = 25;

    private int frames;

    private void runWorldCheck() {
        int wanted = views.gdx.core.DebugFlags.WORLD_CHECK;
        frames++;
        if (wanted < 1 || wanted > cards.size()) {
            return;
        }
        if (frames == WORLD_CHECK_FRAME) {
            com.badlogic.gdx.Gdx.app.log("WorldCheck", "before  " + cardStates());
            clickCard(cards.get(wanted - 1).root);
        } else if (frames == WORLD_CHECK_FRAME + WORLD_CHECK_SETTLE) {
            com.badlogic.gdx.Gdx.app.log("WorldCheck", "settled " + cardStates());
        }
    }

    private void clickCard(Table card) {
        com.badlogic.gdx.math.Vector2 centre = card.localToStageCoordinates(
                new com.badlogic.gdx.math.Vector2(card.getWidth() / 2f, card.getHeight() / 2f));
        stage.getViewport().project(centre);
        // y is flipped: project() returns y-up (OpenGL), the pointer events expect y-down (the mouse).
        int x = (int) centre.x;
        int y = (int) (com.badlogic.gdx.Gdx.graphics.getHeight() - centre.y);
        // Moved onto the card before being pressed, as a real click is. That leaves the card selected
        // AND hovered afterwards -- 1.1 x 1.06 -- which is the widest a card ever gets and therefore the
        // case that decides whether the shelf is wide enough to show an end card without clipping it.
        stage.mouseMoved(x, y);
        stage.touchDown(x, y, 0, com.badlogic.gdx.Input.Buttons.LEFT);
        stage.touchUp(x, y, 0, com.badlogic.gdx.Input.Buttons.LEFT);
    }

    private String cardStates() {
        StringBuilder out = new StringBuilder();
        for (WorldCard card : cards) {
            out.append(card.chapterKey).append(card.selected ? "[*] " : "[ ] ")
                    .append("scale=").append(String.format(java.util.Locale.ROOT, "%.2f",
                            card.root.getScaleX()))
                    .append(" alpha=").append(String.format(java.util.Locale.ROOT, "%.2f",
                            card.root.getColor().a)).append("   ");
        }
        return out.toString();
    }

    // One card: the world's own painting, its name, and how far through it the player is.
    //
    // Selection state is deliberately NOT set here. applySelection owns it, so the same code paints a
    // card on the frame it is built and re-paints it when the player picks a different world.
    private WorldCard worldCard(Chapter chapter, int number) {
        // The frame: a dark border drawn as padding around the art, so a card reads as a card rather
        // than as a photograph butted against its neighbour.
        Table card = new Centred();
        card.setBackground(MenuStyles.panelFill(skin));
        card.pad(CARD_BORDER);
        // Clipped to that padded area, or the frame only has two sides.
        //
        // Scaling.fill covers a box by scaling until BOTH axes are filled, which for a 16:9 painting in
        // a 240x140 hole overflows about four pixels at each side -- and Scene2D does not clip, so those
        // pixels were painted straight over the left and right border. The result was a card framed dark
        // top and bottom and framed in pale stretched artwork down the sides.
        card.setClip(true);
        card.add(cardFace(chapter)).grow();

        WorldCard entry = new WorldCard(card, chapter.getName());
        card.addListener(cardListener(entry, number));
        return entry;
    }

    private Stack cardFace(Chapter chapter) {
        Stack face = new Stack();

        Image art = new Image(worldArt(chapter));
        art.setScaling(Scaling.fill);
        face.add(art);
        face.add(new Image(context.assets().solid(CARD_SHADE)));

        // Text plates. The world paintings are bright and busy at the top AND bottom, so a caption laid
        // straight over one is unreadable on at least one of the four. A dark strip behind each line
        // costs nothing and makes the type legible on every world regardless of what is behind it.
        Table overlay = new Table();
        overlay.add(plated(prettyName(chapter.getName()), MenuStyles.HEADING, null))
                .growX().top().row();
        overlay.add().expand().row();
        overlay.add(plated(progressText(chapter), MenuStyles.TEXT, progressTint(chapter)))
                .growX().bottom().row();
        face.add(overlay);
        return face;
    }

    // NOT ButtonJuice.
    //
    // That is what caused the stuck-scale bug: its exit handler animates back to a hardcoded 1.0, which
    // is the right resting size for a button and the wrong one for a card whose resting size depends on
    // whether its world is selected. A hovered card therefore left the hover at 1.0, and a selected card
    // silently lost its focus scale the first time the mouse crossed it.
    //
    // So the card carries its own listener, and it never animates to a number -- only to whatever its
    // state says it should be, which is the one rule that makes hover and selection compose.
    private ClickListener cardListener(WorldCard card, int number) {
        return new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor from) {
                if (pointer == -1) {
                    card.hovered = true;
                    retarget(card, 0.12f, Interpolation.sine);
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor to) {
                if (pointer == -1) {
                    card.hovered = false;
                    retarget(card, 0.15f, Interpolation.sine);
                }
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                // The model decides whether the world actually changed -- a chapter that refuses to
                // open leaves the key alone, and animating a transition to where we already are would
                // be a flicker for no reason.
                String before = chapterKey();
                commands.submit("menu enter chapter -c " + number);
                if (!java.util.Objects.equals(before, chapterKey())) {
                    selectWorld();
                }
            }
        };
    }

    // Animates a card to where its own state says it belongs, cancelling whatever it was doing.
    private static void retarget(WorldCard card, float seconds, Interpolation ease) {
        float scale = card.scale();
        card.root.clearActions();
        card.root.addAction(Actions.parallel(
                Actions.scaleTo(scale, scale, seconds, ease),
                Actions.alpha(card.alpha(), seconds, Interpolation.fade)));
    }

    // Paints selection onto the cards that already exist.
    //
    // animate is false on the first pass, where there is no previous state to travel from, and true
    // when the player picks a world -- which is the whole difference between the carousel snapping and
    // the carousel moving.
    private void applySelection(boolean animate) {
        String open = chapterKey();
        for (WorldCard card : cards) {
            card.selected = open != null && open.equals(card.chapterKey);
            if (animate) {
                retarget(card, SELECT_SECONDS, Interpolation.swingOut);
            } else {
                card.root.setScale(card.scale());
                card.root.getColor().a = card.alpha();
            }
            if (card.selected) {
                focused = card.root;
            }
        }
        // Bring the selected world into the middle of the pane. Deferred: the row has no laid-out
        // coordinates until Scene2D's next layout pass, so scrolling now would scroll to (0, 0).
        snapToFocused = true;
    }

    // A change of world, as a move rather than a redraw: the cards resize into their new roles, the
    // level path dissolves and returns as the new world's, and the backdrop cross-fades under both.
    private void selectWorld() {
        applySelection(true);
        swapLevels();
        updateBackdrop(true);
    }

    private void swapLevels() {
        levelPath.clearActions();
        levelPath.addAction(Actions.sequence(
                Actions.alpha(0f, SWAP_SECONDS, Interpolation.fade),
                Actions.run(() -> {
                    rebuildLevels();
                    staggerNodes();
                }),
                Actions.alpha(1f, SWAP_SECONDS, Interpolation.fade)));
        // The heading names the world the path belongs to, so it goes with the path rather than
        // flipping to the new name over the old one's levels. Same timings, so the two read as one
        // movement -- rebuildLevels sets the text, and it runs at the bottom of both fades.
        chapterTitle.clearActions();
        chapterTitle.addAction(Actions.sequence(
                Actions.alpha(0f, SWAP_SECONDS, Interpolation.fade),
                Actions.alpha(1f, SWAP_SECONDS, Interpolation.fade)));
    }

    // The new world's levels arriving one after another rather than all at once.
    //
    // Alpha only. The node the player is meant to play next carries a forever scale action, and a second
    // tween writing the same scale would fight it for the whole entrance.
    private void staggerNodes() {
        float delay = 0f;
        for (Actor node : levelPath.getChildren()) {
            node.getColor().a = 0f;
            node.addAction(Actions.sequence(Actions.delay(delay),
                    Actions.alpha(1f, NODE_FADE_SECONDS, Interpolation.fade)));
            delay += NODE_STAGGER;
        }
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
        // clearChildren, NOT clear. This runs from inside the fade that swaps worlds, and Group.clear()
        // takes the actor's own actions with it -- so clear() deleted the very sequence that had just
        // called it, the fade-back-in never ran, and the whole path stayed at alpha 0. An empty strip
        // where four castles should be, with nothing in the log to say why.
        levelPath.clearChildren();
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
        Table node = new Centred();

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
        node.add(medallion(number, unlocked || cleared)).size(BADGE_SIZE, BADGE_SIZE)
                .padTop(BASE_OVERLAP);

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

    // A small dark medallion carrying the level number.
    //
    // Rim disc first, then the darker face inset inside it: two circles give a defined edge without a
    // ring texture, which the dump does not have. That edge is the whole point -- a single flat disc
    // was getting lost against whichever background colour happened to sit behind it.
    private Stack medallion(int number, boolean reachable) {
        Stack medallion = new Stack();
        medallion.add(new Image(context.assets().round(reachable ? BADGE_RIM : BADGE_RIM_LOCKED)));

        Table face = new Table();
        face.add(new Image(context.assets().round(reachable ? BADGE_STONE : BADGE_STONE_LOCKED)))
                .grow().pad(3f);
        medallion.add(face);

        Table caption = new Table();
        Label numberLabel = MenuStyles.label(skin, String.valueOf(number), MenuStyles.HEADING);
        numberLabel.setColor(reachable ? Color.WHITE : DIM);
        caption.add(numberLabel);
        medallion.add(caption);
        return medallion;
    }

    // The row of level nodes, with a dotted trail drawn between them.
    //
    // A Table subclass rather than a ShapeRenderer: the dots are drawn from the SAME Batch as
    // everything else, so they need no second renderer, no separate projection matrix and no
    // begin/end pair interleaved with the Stage's. Overriding draw and calling super LAST is what puts
    // the trail behind the castles -- drawn after, it would run over their faces.
    //
    // It reads its children's real positions, so the trail follows the zigzag automatically and stays
    // correct if the node count or spacing ever changes.
    private static final class LevelPath extends Table {

        private final com.badlogic.gdx.scenes.scene2d.utils.Drawable dot;

        LevelPath(com.badlogic.gdx.scenes.scene2d.utils.Drawable dot) {
            this.dot = dot;
        }

        @Override
        public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
            drawTrail(batch, parentAlpha);
            super.draw(batch, parentAlpha);
        }

        private void drawTrail(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
            com.badlogic.gdx.utils.Array<Actor> nodes = getChildren();
            if (dot == null || nodes.size < 2) {
                return;
            }
            Color previous = batch.getColor().cpy();
            // parentAlpha times THIS actor's own alpha.
            //
            // Scene2D hands an actor its parent's alpha and expects the actor to fold in its own --
            // Group.drawChildren does exactly that on the way down. Drawing the trail at parentAlpha
            // alone therefore ignored the fade the path was in the middle of, and the dots stayed at
            // full strength on an otherwise empty strip while the levels were being swapped.
            batch.setColor(1f, 1f, 1f, parentAlpha * getColor().a);
            for (int i = 0; i < nodes.size - 1; i++) {
                trace(batch, nodes.get(i), nodes.get(i + 1));
            }
            batch.setColor(previous);
        }

        // Dots evenly along the segment between two nodes, skipping the ends so the trail emerges from
        // under each castle rather than colliding with it.
        private void trace(com.badlogic.gdx.graphics.g2d.Batch batch, Actor from, Actor to) {
            float x1 = getX() + from.getX() + from.getWidth() / 2f;
            float x2 = getX() + to.getX() + to.getWidth() / 2f;
            // Anchored at the medallions, which is where a path between two castles should run.
            float y1 = getY() + from.getY() + BADGE_SIZE / 2f;
            float y2 = getY() + to.getY() + BADGE_SIZE / 2f;

            float dx = x2 - x1;
            float dy = y2 - y1;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            int count = (int) (length / DOT_SPACING);
            for (int i = 1; i < count; i++) {
                float t = i / (float) count;
                dot.draw(batch, x1 + dx * t - DOT_SIZE / 2f, y1 + dy * t - DOT_SIZE / 2f,
                        DOT_SIZE, DOT_SIZE);
            }
        }
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
        updateBackdrop(false);
    }

    // Swaps the world behind everything, dissolving out of the old one when asked.
    //
    // The outgoing painting is handed to the upper layer and left at full opacity while the new one is
    // put on the lower layer underneath it, so the fade uncovers rather than blanks: at no point is
    // there a frame showing the title backdrop through a half-transparent world.
    private void updateBackdrop(boolean animate) {
        Chapter chapter = currentChapter();
        TextureRegion art = chapter == null ? null : worldRegion(chapter.getEnvironment());
        if (animate) {
            worldBackdropOut.setDrawable(worldBackdrop.getDrawable());
            worldBackdropOut.getColor().a = 1f;
            worldBackdropOut.clearActions();
            worldBackdropOut.addAction(Actions.fadeOut(BACKDROP_SECONDS, Interpolation.fade));
        }
        worldBackdrop.setDrawable(art == null ? null : new TextureRegionDrawable(art));
    }

    private String chapterKey() {
        Chapter open = currentChapter();
        return open == null ? null : open.getName();
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
