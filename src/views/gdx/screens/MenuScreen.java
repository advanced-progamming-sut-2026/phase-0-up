package views.gdx.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.FitViewport;
import views.gdx.bridge.MenuCommands;
import views.gdx.core.GdxContext;
import views.gdx.core.PvZGame;

// What every menu screen has in common.
//
// Each one is a Stage on the 1280x720 design viewport, a lawn backdrop dimmed by the skin's own modal
// scrim, and a root Table the subclass fills. Doing this per screen would be nine copies of the same
// eight lines, and the interesting differences would be buried in them.
//
// Navigation is deliberately absent from this class. Screens do not push or pop each other: a button
// runs a Command, the Command moves AppSession's current menu, and ScreenManager notices on the next
// frame. That is what keeps the menu graph in one place instead of two -- see MenuCommands.
public abstract class MenuScreen extends ScreenAdapter implements views.gdx.core.Transitioning {

    // The real game's menu backdrop: the time vortex, with its rock islands and central glow. It ships
    // as a single 1024x768 image built for exactly this job, which is why it beats what was here
    // before -- a slice of the Egypt lawn, dimmed until it was legible. That was pale flagstone behind
    // white input fields, so it needed a scrim heavy enough to turn the art into grey mud.
    //
    // The lawn stays as the fallback. It is the only other full-scene image that ships, so a dump
    // missing the menu art still gets a background rather than a flat clear colour.
    // The ten title-screen paintings that ship with the game -- full illustrated scenes, one per world,
    // and by far the most detailed art in the dump that is meant to sit behind a menu.
    //
    // One is chosen per launch rather than per screen, so a session has a consistent look but two
    // sessions do not, which is what the real game does. -Dpvz.backdrop=<ID> pins one.
    private static final String[] BACKDROPS = {
        "IMAGE_TITLEBACKGROUNDS_BACKDROP_A", "IMAGE_TITLEBACKGROUNDS_BACKDROP_B",
        "IMAGE_TITLEBACKGROUNDS_BACKDROP_C", "IMAGE_TITLEBACKGROUNDS_BACKDROP_D",
        "IMAGE_TITLEBACKGROUNDS_BACKDROP_E", "IMAGE_TITLEBACKGROUNDS_BACKDROP_F",
        "IMAGE_TITLEBACKGROUNDS_BACKDROP_G", "IMAGE_TITLEBACKGROUNDS_BACKDROP_H",
        "IMAGE_TITLEBACKGROUNDS_BACKDROP_I", "IMAGE_TITLEBACKGROUNDS_BACKDROP_J"
    };

    // Picked once for the whole run. A fresh pick per screen would reshuffle the artwork every time the
    // player opened a menu, which reads as a glitch rather than as variety.
    private static final String BACKDROP = chooseBackdrop();

    // The time vortex, then the lawn. Both are full-scene images, so a dump missing the title art still
    // gets a background rather than a flat clear colour.
    private static final String BACKDROP_FALLBACK = "IMAGE_MAINMENU_BACKGROUND";
    private static final String BACKDROP_LAST_RESORT = "IMAGE_BACKGROUNDS_EGYPT_TEXTURE";

    private static String chooseBackdrop() {
        String pinned = System.getProperty("pvz.backdrop");
        if (pinned != null && !pinned.isBlank()) {
            return pinned.trim();
        }
        return BACKDROPS[new java.util.Random().nextInt(BACKDROPS.length)];
    }

    // A drifting mote. Eighteen pixels of shipped sparkle, borrowed from the prize-twinkle effect.
    private static final String SPARKLE = "IMAGE_EFFECTS_PRIZE_TWINKLE_PRIZE_TWINKLE_18X18";
    private static final int SPARKLE_COUNT = 22;

    // Much lighter than the lawn needed. The vortex is already dark at the edges and the dialog frame
    // is opaque, so this only has to lift the panel off the art -- not rescue the text from it.
    private static final com.badlogic.gdx.graphics.Color SCRIM =
            new com.badlogic.gdx.graphics.Color(0f, 0f, 0f, 0.25f);

    protected final GdxContext context;
    protected final Skin skin;
    protected final MenuCommands commands;
    protected final Stage stage;

    // Fills the screen. Subclasses add to this.
    protected final Table root = new Table();

    // The whole layer stack, kept so show() can force a layout pass before the entrance runs.
    private final Stack layers;

    // What the Stack actually lays out, so root's own position is left alone. See the constructor.
    private final com.badlogic.gdx.scenes.scene2d.Group slider =
            new com.badlogic.gdx.scenes.scene2d.Group();

    // Between the backdrop and the content. A plain Group, so whatever a screen puts here keeps the
    // position it is given -- a layout widget would reposition its children on every invalidate and
    // undo any drift animation mid-flight.
    protected final com.badlogic.gdx.scenes.scene2d.Group ambient =
            new com.badlogic.gdx.scenes.scene2d.Group();

    private boolean built;

    protected MenuScreen(GdxContext context) {
        this.context = context;
        this.skin = context.assets().skin();
        this.commands = context.menuCommands();
        this.stage = new Stage(new FitViewport(PvZGame.VIRTUAL_WIDTH, PvZGame.VIRTUAL_HEIGHT));

        layers = new Stack();
        layers.setFillParent(true);
        addBackdrop(layers);
        layers.add(ambient);

        // root goes inside a plain Group, and that is what the Stack lays out.
        //
        // This exists so the transition can MOVE the content. A Stack rewrites its children's bounds on
        // every layout pass, and several screens invalidate layout every frame -- ProfileScreen and
        // MainMenuScreen both call setText in refresh() -- so a slide applied to a direct child of the
        // Stack is silently snapped back to y=0 mid-animation. A Group lays out nothing, so whatever
        // position an action puts root at is the position it keeps.
        //
        // setFillParent still sizes root correctly: it only forces SIZE, and it measures against the
        // Group, which the Stack has already sized to the screen.
        slider.addActor(root);
        layers.add(slider);
        stage.addActor(layers);

        scatterSparkles();
    }

    // Backdrop plus scrim. Both are optional: a skin or atlas that cannot supply them leaves the
    // clear colour showing rather than failing to open the screen.
    private void addBackdrop(Stack layers) {
        TextureRegion backdrop = region(BACKDROP);
        if (backdrop == null) {
            backdrop = region(BACKDROP_FALLBACK);
        }
        if (backdrop == null) {
            backdrop = region(BACKDROP_LAST_RESORT);
        }
        if (backdrop != null) {
            Image image = new Image(new TextureRegionDrawable(backdrop));
            // Fill, not stretch: the source is 4:3 and the viewport is 16:9, so fitting it would
            // letterbox the menu itself. Filling crops the top and bottom instead, which the vortex
            // tolerates -- its glow is centred and its edges are empty sky.
            image.setScaling(Scaling.fill);
            breathe(image);
            layers.add(image);
        }
        // The skin's own modal tint is 38% black, which is judged against the muted panels the real
        // game darkens. The lawn is far busier than that -- pale flagstones directly behind white
        // input fields -- so it gets a heavier one. Anything lighter and the form has to compete with
        // the masonry.
        layers.add(new Image(context.assets().solid(SCRIM)));
    }

    // A slow drift on the backdrop, so the vortex is not a still photograph behind a live UI.
    //
    // Scale rather than position, deliberately. The backdrop is a child of a Stack, and a Stack rewrites
    // its children's bounds on every layout pass -- a resize, a font change, anything -- which would
    // snap a moveBy animation back to its start mid-flight. Nothing lays out scale, so it survives.
    // The image is already Scaling.fill, so growing it only pushes more of the art off the edges.
    private static void breathe(Image image) {
        image.setOrigin(com.badlogic.gdx.utils.Align.center);
        image.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.scaleTo(1.06f, 1.06f, 14f,
                                com.badlogic.gdx.math.Interpolation.sine),
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.scaleTo(1f, 1f, 14f,
                                com.badlogic.gdx.math.Interpolation.sine))));
    }

    // How far the content rises on the way in.
    private static final float SLIDE = 50f;
    private static final float ENTER_SECONDS = 0.3f;
    private static final float EXIT_SECONDS = 0.2f;

    // How small the content starts. Overridden by the taller panels, where a gentle 0.94 barely reads.
    protected float entranceScale() {
        return 0.94f;
    }

    // The screen arriving, rather than simply being there: fade up, rise into place, settle open.
    //
    // The slide only works because root sits inside the slider Group -- see the constructor. Applied to
    // a direct child of the Stack it would be undone by the first layout pass, which on several of
    // these screens is the very next frame.
    protected void playEntrance() {
        root.setTransform(true);
        root.setOrigin(com.badlogic.gdx.utils.Align.center);
        root.getColor().a = 0f;
        root.setScale(entranceScale());
        root.moveBy(0f, -SLIDE);
        root.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.parallel(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(ENTER_SECONDS,
                        com.badlogic.gdx.math.Interpolation.fade),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.moveBy(0f, SLIDE, ENTER_SECONDS,
                        com.badlogic.gdx.math.Interpolation.swingOut),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.scaleTo(1f, 1f, ENTER_SECONDS,
                        com.badlogic.gdx.math.Interpolation.swingOut)));
    }

    // The screen leaving. ScreenManager waits for whenDone before swapping.
    @Override
    public void playExit(Runnable whenDone) {
        root.setTransform(true);
        root.setOrigin(com.badlogic.gdx.utils.Align.center);
        // Nothing may be clicked while the screen is on its way out. Without this a second click during
        // the fade queues a second navigation, and the player arrives somewhere they did not ask for.
        stage.getRoot().setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        root.clearActions();
        root.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.parallel(
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeOut(EXIT_SECONDS,
                                com.badlogic.gdx.math.Interpolation.pow2In),
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.scaleTo(0.9f, 0.9f,
                                EXIT_SECONDS, com.badlogic.gdx.math.Interpolation.pow2In)),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.run(whenDone)));
    }

    // Slow motes drifting up through the vortex, on every menu.
    //
    // They live on the ambient Group, which is not a layout widget, so their positions are their own
    // and a moveBy is never undone. Each gets its own speed, size and phase; a shared one would read as
    // a repeating pattern rather than as dust.
    private void scatterSparkles() {
        TextureRegion region = region(SPARKLE);
        if (region == null) {
            return;   // decoration only; its absence costs the screen nothing
        }
        // Fixed seed, so two screenshots of the same screen are comparable.
        java.util.Random random = new java.util.Random(20260813L);
        for (int i = 0; i < SPARKLE_COUNT; i++) {
            ambient.addActor(sparkle(region, random));
        }
    }

    private Image sparkle(TextureRegion region, java.util.Random random) {
        Image mote = new Image(new TextureRegionDrawable(region));
        float size = 6f + random.nextFloat() * 12f;
        mote.setSize(size, size);
        mote.setOrigin(com.badlogic.gdx.utils.Align.center);
        mote.setPosition(random.nextFloat() * PvZGame.VIRTUAL_WIDTH,
                random.nextFloat() * PvZGame.VIRTUAL_HEIGHT);
        mote.getColor().a = 0.25f + random.nextFloat() * 0.35f;

        float rise = 40f + random.nextFloat() * 90f;
        float duration = 6f + random.nextFloat() * 8f;
        com.badlogic.gdx.math.Interpolation sine = com.badlogic.gdx.math.Interpolation.sine;
        mote.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.parallel(
                                com.badlogic.gdx.scenes.scene2d.actions.Actions.moveBy(
                                        0f, rise, duration, sine),
                                com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(
                                        0.7f, duration / 2f)),
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.parallel(
                                com.badlogic.gdx.scenes.scene2d.actions.Actions.moveBy(
                                        0f, -rise, duration, sine),
                                com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(
                                        0.2f, duration / 2f)))));
        mote.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.rotateBy(
                        360f, 18f + random.nextFloat() * 22f)));
        return mote;
    }

    // A missing region is a legitimate answer here, not an error: the caller falls back. Assets.region
    // throws rather than returning null for an unknown id, so the throw is what has to be caught.
    protected TextureRegion region(String id) {
        try {
            return context.assets().region(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // Fill the root table. Called once, on first show() rather than in the constructor, so a subclass
    // is fully initialised before it is asked to lay itself out.
    protected abstract void build(Table root);

    // The track this screen would like, if a file for it exists. Defaults to the shared menu one, so a
    // screen only overrides this when it has a reason -- the greenhouse is the Zen Garden and has its
    // own theme in the soundtrack, and seed selection has a "Choose Your Seeds" cue per world.
    protected String musicTrack() {
        return views.gdx.core.AudioManager.MUSIC_MENU;
    }

    // Called every frame before drawing, for screens whose contents depend on live model state (a
    // badge count, a wallet balance). Most screens do not need it.
    protected void refresh() {
    }

    // What "back" means here. The default runs the same "exit menu" the terminal does, which is what
    // Escape is bound to; a screen with nowhere to go back to overrides it.
    protected void goBack() {
        commands.back();
    }

    @Override
    public void show() {
        // This screen's own track if there is a file for it, otherwise the shared menu one. playMusic
        // no-ops when the winning name is already playing, so walking between screens that share a
        // track does not restart it -- only arriving from the lawn, or from a screen with its own, does.
        context.audio().playMusic(musicTrack(), views.gdx.core.AudioManager.MUSIC_MENU);
        if (!built) {
            built = true;
            build(root);
            // Forces the layout pass from the TOP of the hierarchy down, so root has a real size and
            // its origin can be its actual centre.
            //
            // root.validate() on its own is not enough and quietly does the wrong thing: root fills its
            // parent, the parent has not been sized yet at show() time, so it validates to 0x0, the
            // centred origin lands on (0, 0), and the entrance scales the whole screen out of the
            // bottom-left corner instead of growing from the middle.
            layers.validate();
            root.validate();
            playEntrance();
        }
        Gdx.input.setInputProcessor(stage);
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    goBack();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void render(float delta) {
        refresh();
        runHarness();
        stage.act(delta);
        stage.draw();
    }

    // Late enough that the screen's entrance has finished and the stage is taking input.
    private static final int HARNESS_FRAME = 30;

    private int frames;

    // The unattended-run hooks, both of which drive the screen the way a player would.
    //
    // Deliberately on the base class rather than on one screen. Every menu's Back funnels through
    // goBack() and every menu is made of the same buttons, so the checks belong where all of them
    // inherit them, not where the last bug happened to be.
    private void runHarness() {
        frames++;
        if (frames != HARNESS_FRAME) {
            return;
        }
        if (views.gdx.core.DebugFlags.BACK_CHECK) {
            pressBack();
        }
        String label = views.gdx.core.DebugFlags.CLICK_LABEL;
        if (!label.isEmpty()) {
            pressButton(label);
        }
    }

    // -Dpvz.backCheck=1: presses Escape, and says where that left the session.
    private void pressBack() {
        controllers.engine.MenuType before = context.appSession().getCurrentMenu();
        stage.keyDown(Input.Keys.ESCAPE);
        controllers.engine.MenuType after = context.appSession().getCurrentMenu();
        Gdx.app.log("BackCheck", getClass().getSimpleName() + ": " + before + " -> " + after
                + (before == after ? "   *** Back did nothing ***" : ""));
    }

    // -Dpvz.click=<label>: presses the first button carrying that text.
    private void pressButton(String label) {
        com.badlogic.gdx.scenes.scene2d.ui.TextButton target = findButton(stage.getRoot(), label);
        if (target == null) {
            Gdx.app.error("ClickCheck", "no button labelled \"" + label + "\" on "
                    + getClass().getSimpleName());
            return;
        }
        com.badlogic.gdx.math.Vector2 centre = target.localToStageCoordinates(
                new com.badlogic.gdx.math.Vector2(target.getWidth() / 2f, target.getHeight() / 2f));
        stage.getViewport().project(centre);
        // y is flipped: project() returns y-up (OpenGL), the pointer events expect y-down.
        int x = (int) centre.x;
        int y = (int) (Gdx.graphics.getHeight() - centre.y);
        stage.touchDown(x, y, 0, com.badlogic.gdx.Input.Buttons.LEFT);
        stage.touchUp(x, y, 0, com.badlogic.gdx.Input.Buttons.LEFT);
        Gdx.app.log("ClickCheck", "pressed \"" + label + "\" on " + getClass().getSimpleName());
    }

    // First match in draw order, depth first. Invisible branches are skipped: a hidden panel's buttons
    // are still in the hierarchy and clicking one would do something the player cannot.
    private static com.badlogic.gdx.scenes.scene2d.ui.TextButton findButton(
            com.badlogic.gdx.scenes.scene2d.Group group, String label) {
        for (com.badlogic.gdx.scenes.scene2d.Actor child : group.getChildren()) {
            if (!child.isVisible()) {
                continue;
            }
            if (child instanceof com.badlogic.gdx.scenes.scene2d.ui.TextButton button
                    && button.getText() != null
                    && button.getText().toString().equalsIgnoreCase(label)) {
                return button;
            }
            if (child instanceof com.badlogic.gdx.scenes.scene2d.Group nested) {
                com.badlogic.gdx.scenes.scene2d.ui.TextButton found = findButton(nested, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        // Never leave a disposed Stage wired to the input system: the next screen sets its own
        // processor in show(), but a frame can pass between the two.
        if (Gdx.input.getInputProcessor() == stage) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
