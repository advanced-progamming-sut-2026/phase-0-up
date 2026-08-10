package views.gdx.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import controllers.engine.GameEngine;
import models.game.EnvironmentType;
import models.game.GameSession;
import views.gdx.core.GdxContext;
import views.gdx.map.LawnGeometry;
import views.gdx.render.BackgroundRenderer;
import views.gdx.render.GridOverlayRenderer;

// The lawn.
//
// World units are background pixels (see BackgroundRenderer), so nothing here scales anything: the
// camera alone decides what is on screen, and FitViewport letterboxes rather than stretching, which is
// what the phase-2 spec asks for ("fill the screen without distorting the aspect ratio").
//
// The camera does NOT sit at the middle of the background. The art is 1975 px wide but the 5x9 lawn
// occupies roughly x=345..1500 of it, so centring on the background would push the rightmost column --
// the one zombies walk in from -- off screen. It centres on the LAWN instead.
public final class GameScreen extends ScreenAdapter {

    // How much background width to show at once. 1365 = 768 * 16/9, so on a 16:9 window the view fills
    // it exactly with no letterboxing, while still being wider than the lawn.
    private static final float DEFAULT_VIEW_WIDTH = 1365f;

    private final GdxContext context;
    private final GameSession session;
    private final GameEngine engine;
    private final EnvironmentType environment;

    private final SpriteBatch batch = new SpriteBatch();
    private final OrthographicCamera camera = new OrthographicCamera();
    private final FitViewport viewport;

    private final BackgroundRenderer background;
    private final GridOverlayRenderer grid = new GridOverlayRenderer();
    private final views.gdx.render.GameRenderer entities;
    private final views.gdx.bridge.EntityInterpolator interpolator =
            new views.gdx.bridge.EntityInterpolator();
    private final views.gdx.bridge.GameLoopDriver loop;
    private LawnGeometry lawn;

    private boolean showGrid;

    public GameScreen(GdxContext context, DevBoot boot) {
        this.context = context;
        this.session = boot.session();
        this.engine = boot.engine();
        this.environment = resolveEnvironment(session);

        this.background = new BackgroundRenderer(context.assets());
        background.load(environment);

        this.lawn = LawnGeometry.forEnvironment(environment);
        this.viewport = new FitViewport(viewWidth(), LawnGeometry.WORLD_HEIGHT, camera);

        // Grid defaults on during Phase 1 -- it is the calibration instrument. Once SettingsScreen
        // exists (T4.4) this reads Profile.showGrid instead.
        this.showGrid = !"0".equals(System.getProperty("pvz.grid", "1"));

        this.entities = new views.gdx.render.GameRenderer(
                context.assets(), context.sprites(), lawn, interpolator, environment);
        this.loop = new views.gdx.bridge.GameLoopDriver(engine, session, interpolator);
        this.loop.setGameSpeed(1);

        centreOnLawn();
        Gdx.app.log("GameScreen", "world " + (int) background.totalWidth() + "x"
                + (int) background.height() + " | lawn " + lawn.describe());
    }

    // A level built by MinigameFactory has no template and therefore no chapter to read an environment
    // from; those levels are free to use any background, so Egypt is a safe stand-in.
    private static EnvironmentType resolveEnvironment(GameSession session) {
        try {
            if (session.getLevel() != null && session.getLevel().getTemplate() != null) {
                return EnvironmentType.fromChapter(session.getLevel().getTemplate().getChapter());
            }
        } catch (RuntimeException ignored) {
            // fall through to the default
        }
        return EnvironmentType.ANCIENT_EGYPT;
    }

    private static float viewWidth() {
        // -Dpvz.view=full shows the entire background at once. Only useful while calibrating the lawn
        // against the art, since it letterboxes heavily on a 16:9 window.
        String view = System.getProperty("pvz.view");
        if ("full".equalsIgnoreCase(view)) {
            return 1975f;
        }
        if (view != null && !view.isBlank()) {
            try {
                return Float.parseFloat(view.trim());
            } catch (NumberFormatException ignored) {
                // fall through to the default
            }
        }
        return DEFAULT_VIEW_WIDTH;
    }

    private void centreOnLawn() {
        float lawnCentreX = (lawn.originX() + lawn.rightEdge()) * 0.5f;
        float halfView = viewport.getWorldWidth() * 0.5f;

        // Never show past either end of the painted background -- a strip of clear colour at the edge
        // of the lawn looks like a rendering bug.
        float minX = halfView;
        float maxX = Math.max(halfView, background.totalWidth() - halfView);
        camera.position.set(Math.max(minX, Math.min(maxX, lawnCentreX)),
                LawnGeometry.WORLD_HEIGHT * 0.5f, 0f);
        camera.update();
    }

    @Override
    public void render(float delta) {
        // Model first, then draw what it produced. Both the tick and the animation clock stop when the
        // loop is paused or the level is over, which is what freezes the board.
        loop.update(delta);
        // Animations advance only while the game runs, so pausing freezes the board as the spec
        // requires. Renderers keep their own per-entity clocks; this is just the gate.
        float animationDelta = (!loop.isPaused() && loop.isPlaying()) ? delta : 0f;

        camera.update();
        viewport.apply();
        batch.setProjectionMatrix(camera.combined);

        batch.begin();
        background.draw(batch);
        entities.draw(batch, session, animationDelta, loop.alpha());
        batch.end();

        // Drawn after the sprites so the lines sit over the board, not under it.
        if (showGrid) {
            grid.draw(camera.combined, lawn);
        }

        if (debugCounts) {
            logBoardCounts();
        }

        // Gameplay narration: the model queues events during the tick and the view drains them here.
        // Same seam the terminal build renders through, so nothing had to be added to the model.
        for (utils.Result event : session.drainEvents()) {
            context.toasts().show(event);
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
        centreOnLawn();
    }

    // -Dpvz.debugCounts=1 reports what the model actually contains each second, so "is it not being
    // drawn, or is it not there?" can be answered without guessing from a screenshot.
    private final boolean debugCounts = views.gdx.core.DebugFlags.BOARD_COUNTS;
    private int lastLoggedSecond = -1;

    private void logBoardCounts() {
        int second = (int) (loop.ticksRun() / utils.Constants.TICKS_PER_SECOND);
        if (second == lastLoggedSecond) {
            return;
        }
        lastLoggedSecond = second;
        int projectiles = 0;
        int zombies = 0;
        int plants = 0;
        for (models.map.Row row : session.getMap().getRows()) {
            projectiles += row.getActiveProjectiles().size();
            zombies += row.getZombies().size();
            for (models.map.Cell cell : row.getCells()) {
                if (cell.hasPlant() && !cell.getCurrentPlant().isDead()) {
                    plants++;
                }
            }
        }
        Gdx.app.log("Board", "t=" + second + "s plants=" + plants + " zombies=" + zombies
                + " projectiles=" + projectiles + " suns=" + session.getActiveSuns().size()
                + " tracked=" + interpolator.trackedCount());
        reportLaneDesync();
    }

    // A zombie's lane is stored twice: in movement.y, and implicitly by which Row's list holds it.
    // CombatSystem.reconcileZombieLanes is the only thing that reconciles them. If they drift, a
    // zombie is DRAWN where movement.y says but FOUGHT where the Row list says -- which looks exactly
    // like "it took damage from a plant in another lane". Worth knowing which of the two is happening
    // before blaming the renderer.
    private void reportLaneDesync() {
        for (int row = 0; row < utils.Constants.BOARD_ROWS; row++) {
            for (models.entities.zombies.Zombie z : session.getMap().getRow(row).getZombies()) {
                int movementLane = z.getMovement().getPositionY();
                if (movementLane != row) {
                    Gdx.app.error("LaneDesync", z.getAlias() + " x=" + String.format("%.2f", z.getX())
                            + " is in Row " + row + " but movement.y=" + movementLane);
                }
                if (movementLane < 0 || movementLane >= utils.Constants.BOARD_ROWS) {
                    Gdx.app.error("LaneDesync", z.getAlias() + " has out-of-range lane " + movementLane);
                }
            }
        }
    }

    public GameSession session() {
        return session;
    }

    public GameEngine engine() {
        return engine;
    }

    public LawnGeometry lawn() {
        return lawn;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    @Override
    public void dispose() {
        batch.dispose();
        grid.dispose();
    }
}
