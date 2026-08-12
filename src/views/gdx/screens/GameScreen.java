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

    // Interaction (phase 2). ToolState is what the cursor holds between two clicks; CommandBridge turns
    // the second click into the same command string the terminal would have been typed.
    private final views.gdx.input.ToolState tools = new views.gdx.input.ToolState();
    private final views.gdx.bridge.CommandBridge commands;
    private final views.gdx.input.LawnInputProcessor lawnInput;
    private final views.gdx.ui.GameHud hud;
    private final views.gdx.render.CursorRenderer cursor;

    // Wash over the tile under the cursor. Faint on purpose: it has to read as "this one" without
    // hiding the plant already standing there.
    private static final com.badlogic.gdx.graphics.Color HOVER_TINT =
            new com.badlogic.gdx.graphics.Color(1f, 1f, 1f, 0.22f);
    // The shovel is destructive and does not ask twice, so its highlight is red rather than white.
    private static final com.badlogic.gdx.graphics.Color SHOVEL_TINT =
            new com.badlogic.gdx.graphics.Color(1f, 0.25f, 0.2f, 0.3f);

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
        // exists (T4.4) this reads Profile.showGr instead.
        this.showGrid = !"0".equals(System.getProperty("pvz.grid", "1"));

        this.entities = new views.gdx.render.GameRenderer(
                context.assets(), context.sprites(), lawn, interpolator, environment);
        this.loop = new views.gdx.bridge.GameLoopDriver(engine, session, interpolator);
        this.loop.setGameSpeed(1);
        this.loop.setPaused(views.gdx.core.DebugFlags.START_PAUSED);

        // Command output goes to toasts from here on -- otherwise every "not enough sun" would be
        // printed to a console nobody is looking at.
        // The explosion effect taps this stream rather than session.drainEvents(): GameEngine drains
        // the model's events itself during the tick, so the screen's own drain sees nothing.
        engine.setInGameRenderer(new views.gdx.renderers.GdxInGameRenderer(context.toasts(),
                message -> entities.explosions().onEvent(message)));

        this.commands = new views.gdx.bridge.CommandBridge(engine::submitInGameCommand);
        this.lawnInput = new views.gdx.input.LawnInputProcessor(
                viewport, lawn, session, commands, tools, entities.collectibles());
        this.hud = new views.gdx.ui.GameHud(context.assets(), context.sprites(), session, tools);
        this.cursor = new views.gdx.render.CursorRenderer(context.sprites(),
                new views.gdx.ui.UiArt(context.assets()), lawn);
        // Cheat buttons synthesise the same command strings the prompt accepts, through the same door.
        hud.installCheats(engine::submitInGameCommand);
        this.showcase = new Showcase(engine, session);

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
        // The held plant or tool, drawn last inside the same scaled pass so it sits over the board.
        // It uses `delta`, not `animationDelta`: the cursor keeps animating while the game is paused,
        // because it belongs to the player rather than to the simulation.
        com.badlogic.gdx.math.Matrix4 cursorTransform = views.gdx.render.SpritePlacer.beginScaled(batch);
        try {
            cursor.draw(batch, tools, lawnInput.cursorWorldX(), lawnInput.cursorWorldY(), delta);
        } finally {
            views.gdx.render.SpritePlacer.endScaled(batch, cursorTransform);
        }
        batch.end();

        // Under the grid but over the sprites: a wash on the tile the cursor is on, so it is obvious
        // where a click will land before it costs anything.
        views.gdx.map.GridPos hovered = lawnInput.hovered();
        if (hovered.isValid() && tools.isHolding()) {
            grid.highlight(camera.combined, lawn, hovered.col(), hovered.row(),
                    tools.tool() == views.gdx.input.ToolState.Tool.SHOVEL ? SHOVEL_TINT : HOVER_TINT);
        }

        // Drawn after the sprites so the lines sit over the board, not under it.
        if (showGrid) {
            grid.draw(camera.combined, lawn);
        }

        if (debugCounts) {
            logBoardCounts();
        }

        // Last, so it sits over the board and the grid. Its own viewport, so it does not move with the
        // camera as the lawn is centred.
        hud.setPaused(loop.isPaused());
        hud.update(loop.ticksRun());
        hud.render(delta);

        // After the HUD has drawn, never before. Scene2D does not position anything until its first
        // layout pass, so a check that runs on frame zero finds every widget at (0, 0) with no size and
        // concludes -- wrongly -- that clicks miss them.
        if (inputCheckFrames > 0 && --inputCheckFrames == 0) {
            runInputCheck();
        }

        advanceShowcase();

        // Gameplay narration: the model queues events during the tick and the view drains them here.
        // Same seam the terminal build renders through, so nothing had to be added to the model.
        for (utils.Result event : session.drainEvents()) {
            // The same stream feeds two consumers. Explosions ignore anything that is not a detonation,
            // so neither has to know about the other.
            if (debugCounts) {
                Gdx.app.log("Event", event.message());
            }
            entities.explosions().onEvent(event.message());
            context.toasts().show(event);
        }
    }

    // An InputMultiplexer rather than setInputProcessor(lawnInput) directly: the HUD's Scene2D stage
    // has to get first refusal on every event, so clicking a seed card does not also plant on the tile
    // behind it. Whatever is added first wins, and the lawn is deliberately last.
    @Override
    public void show() {
        com.badlogic.gdx.InputMultiplexer input = new com.badlogic.gdx.InputMultiplexer();
        input.addProcessor(new KeyboardTools());
        // The HUD gets the event before the lawn does. Without this, clicking a seed card would arm the
        // seed AND immediately plant it on whatever tile happens to lie behind the card.
        input.addProcessor(hud.stage());
        input.addProcessor(lawnInput);
        Gdx.input.setInputProcessor(input);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    // Keyboard shortcuts for the tools, so shovel and plant food are usable before the HUD buttons
    // exist (T2.6/T2.8). Digits pick a seed slot, the same way the original binds them.
    private final class KeyboardTools extends com.badlogic.gdx.InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            if (keycode == com.badlogic.gdx.Input.Keys.S) {
                tools.selectTool(views.gdx.input.ToolState.Tool.SHOVEL);
                return true;
            }
            if (keycode == com.badlogic.gdx.Input.Keys.F) {
                tools.selectTool(views.gdx.input.ToolState.Tool.PLANT_FOOD);
                return true;
            }
            if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
                tools.clear();
                return true;
            }
            if (keycode == com.badlogic.gdx.Input.Keys.C) {
                hud.toggleCheats();
                return true;
            }
            // Pausing stops the accumulator, so the model AND every animation clock freeze together --
            // a paused board that keeps waving its leaves does not read as paused.
            if (keycode == com.badlogic.gdx.Input.Keys.P
                    || keycode == com.badlogic.gdx.Input.Keys.SPACE) {
                loop.togglePause();
                return true;
            }
            if (keycode >= com.badlogic.gdx.Input.Keys.NUM_1
                    && keycode <= com.badlogic.gdx.Input.Keys.NUM_9) {
                selectSeedSlot(keycode - com.badlogic.gdx.Input.Keys.NUM_1);
                return true;
            }
            return false;
        }
    }

    // Arms the seed in the given slot of the player's chosen bank. Reads the live packet list rather
    // than a cached copy, so a level that adjusts the slots (Locked Plants, I Zombie) is respected.
    private void selectSeedSlot(int slot) {
        java.util.List<models.game.SeedPacket> packets = session.getSelectedSeeds();
        if (packets == null || slot < 0 || slot >= packets.size()) {
            return;
        }
        tools.selectSeed(packets.get(slot).getPlantType());
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
        hud.resize(width, height);
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

    // -Dpvz.inputCheck=1 verifies the half of the input path that no unit test can reach: turning a
    // screen pixel back into a lawn tile. Every tile's centre is projected to a screen position and fed
    // straight to the input processor, which must report that same tile. Anything else means clicks land
    // on the wrong square -- silently, since a wrong-but-valid tile still plants something somewhere.
    private int inputCheckFrames = views.gdx.core.DebugFlags.INPUT_CHECK ? 3 : 0;

    // Built in the constructor, not here: field initialisers run before the constructor body, so
    // engine and session are still null at this point.
    private final Showcase showcase;
    private int showcaseFrames = views.gdx.core.DebugFlags.SHOWCASE ? 5 : 0;
    private int showcaseFeedFrames;
    private int showcaseSunFrames;

    // -Dpvz.showcase=1, in three stages. The board is built a few frames in; the Snow Pea is fed once
    // its zombies have actually walked into the lane; and the radioactive sun is caught while it is
    // still in the air, which is the only state in which it detonates.
    private void advanceShowcase() {
        if (showcaseFrames > 0 && --showcaseFrames == 0) {
            showcase.run(tools);
            hud.toggleCheats();          // panel open, so it can be seen and clicked
            showcaseFeedFrames = 120;
        }
        if (showcaseFeedFrames > 0 && --showcaseFeedFrames == 0) {
            showcase.feedSnowPea();
            showcaseSunFrames = 40;      // still mid-fall at this point
        }
        if (showcaseSunFrames > 0 && --showcaseSunFrames == 0) {
            showcase.detonateRadioactiveSun();
        }
    }

    // Viewport.project returns y measured UP from the bottom (OpenGL's convention), while unproject --
    // and every real mouse event -- measures y DOWN from the top. LibGDX does not make the pair
    // symmetric, so the flip has to happen here. Without it this check reports all 45 tiles as wrong
    // and blames the input processor, which is reading the axis correctly.
    private int toMouseY(float projectedY) {
        return (int) (Gdx.graphics.getHeight() - projectedY);
    }

    private void runInputCheck() {
        com.badlogic.gdx.math.Vector3 point = new com.badlogic.gdx.math.Vector3();
        int mismatches = 0;
        for (int row = 0; row < utils.Constants.BOARD_ROWS; row++) {
            for (int col = 0; col < utils.Constants.BOARD_COLS; col++) {
                point.set(lawn.centerX(col), lawn.centerY(row), 0f);
                viewport.project(point);
                lawnInput.mouseMoved((int) point.x, toMouseY(point.y));

                views.gdx.map.GridPos got = lawnInput.hovered();
                if (got.col() != col || got.row() != row) {
                    mismatches++;
                    Gdx.app.error("InputCheck", "tile (" + col + ", " + row + ") -> screen "
                            + (int) point.x + "," + toMouseY(point.y) + " -> " + got);
                }
            }
        }
        Gdx.app.log("InputCheck", mismatches == 0
                ? "all " + (utils.Constants.BOARD_COLS * utils.Constants.BOARD_ROWS)
                        + " tiles round-tripped correctly"
                : mismatches + " tiles did NOT round-trip");

        checkToolActions(point);
        checkHudClaimsItsOwnClicks();
    }

    // The HUD is first in the InputMultiplexer so a click on a seed card cannot also plant on the tile
    // behind it. That is an ordering claim, and ordering claims are exactly the kind that survive review
    // and then quietly stop being true, so it is asserted rather than trusted.
    private void checkHudClaimsItsOwnClicks() {
        com.badlogic.gdx.math.Vector2 centre = hud.firstCardCentre();
        if (centre == null) {
            return;
        }
        com.badlogic.gdx.math.Vector3 point =
                new com.badlogic.gdx.math.Vector3(centre.x, centre.y, 0f);
        hud.stage().getViewport().project(point);
        int screenX = (int) point.x;
        int screenY = toMouseY(point.y);

        tools.clear();
        boolean consumed = hud.stage().touchDown(screenX, screenY, 0, com.badlogic.gdx.Input.Buttons.LEFT);
        hud.stage().touchUp(screenX, screenY, 0, com.badlogic.gdx.Input.Buttons.LEFT);

        Gdx.app.log("InputCheck", "seed card click: consumed=" + consumed
                + " armed=" + tools.tool() + " seed=" + tools.seedName()
                + (consumed ? "" : "  <-- would fall through to the lawn"));

        // Leaves the shovel armed with the cursor still parked from checkToolActions, so pairing this
        // flag with -Dpvz.screenshot captures the held-tool cursor and the tile highlight. Clearing
        // here instead is what made both of them invisible in the capture.
        tools.selectTool(views.gdx.input.ToolState.Tool.SHOVEL);

        // Plant food, end to end: feed the Peashooter at (1, 0) and count the shots it produces. The
        // effect is a queued burst, so a working feed shows up as projectiles appearing immediately.
        engine.submitInGameCommand("cheat add-plant-food");
        int shotsBefore = countProjectiles();
        boolean fedOk = engine.submitInGameCommand("feed plant -l (1, 0)");
        models.map.Cell fedCell = session.getMap().getRow(0).cellAt(1);
        // Ticks have to elapse: queueBurst only queues, and the shots come out over later updates.
        // Counting immediately after the feed reports 0 and looks like the burst did nothing.
        engine.submitInGameCommand("advance time -t 20 ticks");
        Gdx.app.log("InputCheck", "feed accepted=" + fedOk
                + " plant=" + (fedCell.hasPlant() ? fedCell.getCurrentPlant().getName() : "EMPTY")
                + " hasFood=" + (fedCell.hasPlant() && fedCell.getCurrentPlant().hasPlantFood())
                + " active=" + (fedCell.hasPlant() && fedCell.getCurrentPlant().isPlantFoodActive())
                + " food-left=" + session.getPlantFoodCount()
                + " shots " + shotsBefore + " -> " + countProjectiles());

        checkSnowPeaPlantFood();
        checkFeedWhileShooting();

        checkWallnutPlantFood();

        // A detonation, so the two-layer explosion can be seen at all: nothing in a normal opening
        // minute blows up, and the effect is driven entirely by the model's narration.
        boolean bomb = engine.submitInGameCommand("plant plant -t Cherry Bomb -l (5, 2)");
        models.map.Cell bombCell = session.getMap().getRow(2).cellAt(5);
        Gdx.app.log("InputCheck", "cherry bomb command accepted: " + bomb
                + " cell now: " + (bombCell.hasPlant()
                        ? bombCell.getCurrentPlant().getName() : "EMPTY"));
        if (bombCell.hasPlant()) {
            models.entities.plants.Plant bombPlant = bombCell.getCurrentPlant();
            StringBuilder abilities = new StringBuilder();
            if (bombPlant.getAbilities() != null) {
                for (models.entities.plants.abilities.PlantAbility a : bombPlant.getAbilities()) {
                    abilities.append(a.getClass().getSimpleName()).append(' ');
                }
            }
            Gdx.app.log("InputCheck", "  bomb: dead=" + bombPlant.isDead()
                    + " disabled=" + bombPlant.isDisabled()
                    + " abilities=[" + abilities.toString().trim() + "]");
        }
    }

    // The whole chain end to end: arm a seed, click a tile, see whether a plant is standing on it
    // afterwards -- then shovel it out again, then collect a sun by hovering.
    private void checkToolActions(com.badlogic.gdx.math.Vector3 point) {
        if (session.getSelectedSeeds() != null && !session.getSelectedSeeds().isEmpty()) {
            // Sun and cooldown are both topped up first. The level pre-plants a Sunflower, which puts
            // that packet straight onto its 5s recharge -- a real rule, correctly enforced, but it would
            // make this check report a failure that is nothing to do with the input path.
            engine.submitInGameCommand("cheat add -n 5000 suns");
            engine.submitInGameCommand("cheat remove-cooldown");
            String seed = session.getSelectedSeeds().get(0).getPlantType();
            int col = utils.Constants.BOARD_COLS - 2;
            int row = 0;

            // Log every Result the click produces instead of toasting it: a refusal ("there is already
            // a plant there", "that seed is recharging") is the whole answer when this reports NOTHING,
            // and a toast in a headless smoke run goes nowhere anyone can read it.
            engine.setInGameRenderer(new views.renderers.InGameRenderer() {
                @Override
                public void render(utils.Result result) {
                    Gdx.app.log("InputCheck", "  engine said: " + result.message());
                }
            });
            tools.selectSeed(seed);
            point.set(lawn.centerX(col), lawn.centerY(row), 0f);
            viewport.project(point);
            lawnInput.touchDown((int) point.x, toMouseY(point.y), 0,
                    com.badlogic.gdx.Input.Buttons.LEFT);

            models.map.Cell cell = session.getMap().getRow(row).cellAt(col);
            Gdx.app.log("InputCheck", "click-to-plant " + seed + " at (" + col + ", " + row + "): "
                    + (cell.hasPlant() ? "PLANTED " + cell.getCurrentPlant().getName() : "NOTHING"));

            // Shovel the same tile back out, so the other half of the tool path is covered too.
            tools.selectTool(views.gdx.input.ToolState.Tool.SHOVEL);
            lawnInput.touchDown((int) point.x, toMouseY(point.y), 0,
                    com.badlogic.gdx.Input.Buttons.LEFT);
            Gdx.app.log("InputCheck", "shovel at (" + col + ", " + row + "): "
                    + (cell.hasPlant() ? "STILL THERE" : "CLEARED"));

            // Hover-to-collect: drop a sun on a known tile, move the cursor onto it, expect the wallet
            // to grow and the sun to leave the board.
            int before = session.getSunAmount();
            int sunCol = 2;
            int sunRow = 3;
            session.addSun(new models.entities.collectibles.Sun(
                    sunCol + 0.5, sunRow, sunRow,
                    models.entities.collectibles.SunType.NORMAL, 25, false, 100));
            point.set(lawn.centerX(sunCol), lawn.centerY(sunRow), 0f);
            viewport.project(point);
            tools.clear();
            lawnInput.touchDown((int) point.x, toMouseY(point.y), 0,
                    com.badlogic.gdx.Input.Buttons.LEFT);
            Gdx.app.log("InputCheck", "click-collect: sun " + before + " -> "
                    + session.getSunAmount());

            engine.setInGameRenderer(new views.gdx.renderers.GdxInGameRenderer(context.toasts(),
                    message -> entities.explosions().onEvent(message)));

            // Leave the shovel armed with the cursor parked on a tile, so pairing this flag with
            // -Dpvz.screenshot captures the hover highlight -- the one part of the input work that can
            // only be checked by looking.
            tools.selectTool(views.gdx.input.ToolState.Tool.SHOVEL);
            point.set(lawn.centerX(4), lawn.centerY(2), 0f);
            viewport.project(point);
            lawnInput.mouseMoved((int) point.x, toMouseY(point.y));
        }
    }

    // Does Snow Pea's plant food actually do anything? Its two effects are a lane freeze and a shot
    // burst, and NEITHER has art wired up yet -- so "I don't see it apply" could equally mean it is
    // firing invisibly. Chill is the model's own record of the freeze landing, so it settles the
    // question without needing to see anything.
    private void checkSnowPeaPlantFood() {
        int[] found = findPlant("Snow Pea");
        if (found == null) {
            Gdx.app.log("SnowPea", "no Snow Pea on the board to feed");
            return;
        }
        int col = found[0];
        int row = found[1];

        // Something to freeze, parked in the same lane.
        engine.submitInGameCommand("cheat spawn-zombie -t normal -l (8, " + row + ")");
        engine.submitInGameCommand("cheat add-plant-food");
        boolean fed = engine.submitInGameCommand("feed plant -l (" + col + ", " + row + ")");

        // Ticks have to actually elapse for the strategy to run and the chill to be stamped on.
        engine.submitInGameCommand("advance time -t 20 ticks");

        int chilled = 0;
        int total = 0;
        for (models.entities.zombies.Zombie z : session.getMap().getRow(row).getZombies()) {
            total++;
            if (z.getState().isChilled() || z.getState().getChilledTimer() > 0) {
                chilled++;
            }
        }
        Gdx.app.log("SnowPea", "fed=" + fed + " at (" + col + ", " + row + ")"
                + "  zombies in lane=" + total + "  chilled=" + chilled
                + "  shots=" + countProjectiles());
    }

    // The case that actually broke: feeding a plant that is MID-WIND-UP, rather than idle. A zombie is
    // parked in the lane first so the shooter is genuinely firing, then the feed lands during the
    // wind-up window and the burst has to survive it.
    private void checkFeedWhileShooting() {
        // A FRESH plant, never fed. Reusing one from an earlier check leaves its previous burst still
        // running, which masks whether this feed survived.
        int col = 3;
        int row = 4;
        engine.submitInGameCommand("cheat add -n 500 suns");
        engine.submitInGameCommand("cheat remove-cooldown");
        engine.submitInGameCommand("plant plant -t Peashooter -l (" + col + ", " + row + ")");
        engine.submitInGameCommand("cheat spawn-zombie -t Basic -l (8, " + row + ")");

        models.map.Cell cell = session.getMap().getRow(row).cellAt(col);
        if (!cell.hasPlant()) {
            Gdx.app.log("FeedMidShot", "could not place a test Peashooter");
            return;
        }

        engine.submitInGameCommand("cheat add-plant-food");
        // Part-way into a wind-up: the shot is committed but has not left yet. This is the window that
        // used to swallow the burst.
        engine.submitInGameCommand("advance time -t 2 ticks");
        engine.submitInGameCommand("feed plant -l (" + col + ", " + row + ")");
        engine.submitInGameCommand("advance time -t 6 ticks");

        // The queue itself, not a projectile count: shots expire off the board, so counting them
        // undercounts a burst that is working.
        Gdx.app.log("FeedMidShot", "fed a winding-up Peashooter at (" + col + ", " + row + "): "
                + "burst still queued = " + cell.getCurrentPlant().isPlantFoodActive()
                + "  (false here is the bug)");
    }

    // Wall-nut is the plant that actually ships plantfood_on and plantfood_off, so it is the only way
    // to exercise the full three-stage sequence. Fed here and then left alone: the stages are chosen by
    // the RENDERER, so they only appear as the game draws frames -- advancing ticks synchronously would
    // run the whole boost past without a single clip ever being selected.
    private void checkWallnutPlantFood() {
        int[] nut = findPlant("Wall-nut");
        if (nut == null) {
            return;
        }
        engine.submitInGameCommand("cheat add-plant-food");
        boolean ok = engine.submitInGameCommand("feed plant -l (" + nut[0] + ", " + nut[1] + ")");
        Gdx.app.log("NutFood", "fed Wall-nut at (" + nut[0] + ", " + nut[1] + ") = " + ok
                + "; watch ClipChange for plantfood_on -> plantfood -> plantfood_off");
    }

    private int[] findPlant(String name) {
        for (int row = 0; row < utils.Constants.BOARD_ROWS; row++) {
            for (int col = 0; col < utils.Constants.BOARD_COLS; col++) {
                models.map.Cell cell = session.getMap().getRow(row).cellAt(col);
                if (cell.hasPlant() && name.equalsIgnoreCase(cell.getCurrentPlant().getName())) {
                    return new int[] {col, row};
                }
            }
        }
        return null;
    }

    private int countProjectiles() {
        int total = 0;
        for (models.map.Row row : session.getMap().getRows()) {
            total += row.getActiveProjectiles().size();
        }
        return total;
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
        hud.dispose();
    }
}
