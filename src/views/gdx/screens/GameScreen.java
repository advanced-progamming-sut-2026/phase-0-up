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
    private final views.gdx.render.DangerGlow danger = new views.gdx.render.DangerGlow();
    private final views.gdx.core.AudioCues cues;
    private final String[] lawnMusic;
    private final String[] finalWaveMusic;
    private final views.gdx.render.ModeOverlayRenderer modeOverlay =
            new views.gdx.render.ModeOverlayRenderer();
    private final views.gdx.render.GameRenderer entities;
    private final views.gdx.bridge.EntityInterpolator interpolator =
            new views.gdx.bridge.EntityInterpolator();
    // Whether this screen is running a game or watching one -- GameLoopDriver ticks the real engine,
    // NetLoopDriver applies the server's snapshots. Everything downstream (the animation gate, the
    // interpolation alpha, the pause panel) goes through LoopDriver and does not care which it got.
    //
    // Both are handed THIS screen's interpolator, the same instance GameRenderer draws through: a
    // driver built around a second one would smooth a board nobody is looking at, while the renderer
    // read an empty set of tracks and drew every entity at its raw 10 Hz position.
    private final views.gdx.bridge.LoopDriver loop;
    // Non-null only in a two-player match. Everything about this screen that behaves differently in
    // one is a branch on this field, and there are seven of them: which driver ticks the board, where
    // commands go, which HUD panel is built, whether an objective card appears, what "leave" means,
    // and which of the two end-of-match banners is shown.
    private final MatchBoot match;
    // Two players on one device. `couchPlay` is known at construction because the HUD has to be built
    // knowing it -- both panels, both banks -- while `couch` itself is assembled in openBoard, which
    // is the first moment the CommandBridge it drives exists.
    private final boolean couchPlay;
    private CouchPlay couch;
    // The match's driver, held under its own type so the snapshot and match-over hooks are reachable.
    // `loop` above is the same object seen through LoopDriver. Null outside a match.
    private final views.gdx.bridge.NetLoopDriver netLoop;
    // Where the model's narration lands.
    //
    // Command output goes to toasts rather than to a console nobody is looking at, and every view
    // effect taps the SAME stream through onModelEvent: explosions ignore anything that is not a
    // detonation, the weather ignores anything that is not a tornado, and neither has to know about
    // the other. They read it here rather than from session.drainEvents() because GameEngine drains
    // the model itself during its tick, so the screen's own drain would see nothing.
    //
    // A field rather than a local, because in a match this is fed from the NETWORK as well as from
    // the engine -- the server relays the same sentences the model wrote. See MatchPlay.
    private final views.gdx.renderers.GdxInGameRenderer narration;
    private final views.gdx.render.CameraShake shake = new views.gdx.render.CameraShake();
    // Where the camera rests. A shake is applied as an offset FROM this rather than added to the live
    // position, which would accumulate and walk the board off screen over a level's worth of blasts.
    private float cameraBaseX;
    private float cameraBaseY;
    private LawnGeometry lawn;

    // Interaction (phase 2). ToolState is what the cursor holds between two clicks; CommandBridge turns
    // the second click into the same command string the terminal would have been typed.
    private final views.gdx.input.ToolState tools = new views.gdx.input.ToolState();
    // The payoff for CommandBridge, and the whole reason this project could be networked at all: a
    // click has ALWAYS produced a command string and posted it somewhere. In a match that somewhere is
    // a socket instead of the local engine. Not one line of CommandBridge changes.
    private final views.gdx.bridge.CommandBridge commands;
    private final views.gdx.input.LawnInputProcessor lawnInput;
    private final views.gdx.ui.GameHud hud;
    private final views.gdx.render.CursorRenderer cursor;
    private final views.gdx.ui.GameOverlays overlays;

    // The unattended drivers for the three mini-games. Off unless one of their flags is set.
    private final MinigameHarness harness;

    // The scoring game's floating awards. Silent on every other board.
    private final views.gdx.ui.ScorePopups scorePopups;
    // Dropped rewards flying from the corpse to the counter that absorbed them.
    private final views.gdx.ui.PickupFlights pickups;

    // Crazy Dave, Penny and Zomboss. Silent unless the model says something worth interrupting for.
    private final views.gdx.ui.NpcDialogueBox npc;

    // -Dpvz.inputCheck and the four scenarios that ride along with it. Lifted out when this class hit
    // Checkstyle's 500-NCSS ceiling for the second time; see InputCheck.
    private final InputCheck inputCheck;

    // Wash over the tile under the cursor. Faint on purpose: it has to read as "this one" without
    // hiding the plant already standing there.
    private static final com.badlogic.gdx.graphics.Color HOVER_TINT =
            new com.badlogic.gdx.graphics.Color(1f, 1f, 1f, 0.22f);
    // The shovel is destructive and does not ask twice, so its highlight is red rather than white.
    private static final com.badlogic.gdx.graphics.Color SHOVEL_TINT =
            new com.badlogic.gdx.graphics.Color(1f, 0.25f, 0.2f, 0.3f);
    // A tile the shovel will be refused on -- Save Our Seeds' defended plants. Grey, not red: red says
    // "this will be destroyed", and the whole point is that it will not.
    private static final com.badlogic.gdx.graphics.Color NO_DIG_TINT =
            new com.badlogic.gdx.graphics.Color(0.55f, 0.55f, 0.58f, 0.34f);
    // The Beghouled tile the player has picked up. Stronger than the hover wash on purpose -- it has to
    // still be obvious after the cursor has moved off it looking for a neighbour.
    private static final com.badlogic.gdx.graphics.Color SWAP_TINT =
            new com.badlogic.gdx.graphics.Color(1f, 0.9f, 0.35f, 0.42f);
    // The keyboard player's cursor in couch play. Sickly green, so at a glance it is obviously the
    // ZOMBIE player's mark and not the other one's white hover wash.
    private static final com.badlogic.gdx.graphics.Color COUCH_TINT =
            new com.badlogic.gdx.graphics.Color(0.45f, 0.85f, 0.3f, 0.38f);

    // The player's own setting, with -Dpvz.grid still able to force it either way for calibration
    // work. The grid was on by default through Phase 1 because it WAS the calibration instrument; now
    // that Settings owns it, the default is whatever the profile says. See gridSetting.
    private boolean showGrid;

    // The real way in: the level is already chosen and its seeds already picked, so the session is
    // waiting on the AppSession and all this has to do is give it an engine and start ticking.
    //
    // init() is what startLoop() would have called first -- it places a mode's pre-set plants, applies
    // seed boosts and arms quest tracking -- and skipping it produces a board that looks right and
    // quietly plays by the wrong rules.
    public GameScreen(GdxContext context) {
        this(context, context.appSession().getCurrentGameSession(),
                newEngine(context));
    }

    private static GameEngine newEngine(GdxContext context) {
        GameEngine engine = new GameEngine(context.appSession().getCurrentGameSession(),
                context.renderers());
        engine.init();
        return engine;
    }

    public GameScreen(GdxContext context, DevBoot boot) {
        this(context, boot.session(), boot.engine(), null, false);
    }

    // A two-player match. The board arrives from the server rather than being simulated here -- see
    // MatchBoot, which owns that distinction so this constructor does not have to.
    public GameScreen(GdxContext context, MatchBoot match) {
        this(context, match.session(), match.engine(), match, false);
    }

    // Two players on this device, no server. Same mode and same rules as a networked match -- see
    // CouchBoot, which is deliberately the shortest class in this package.
    //
    // A factory rather than a public constructor taking a CouchBoot, so that CouchBoot itself can stay
    // package-private: it is an assembly detail of this screen and nothing outside needs to name it.
    public static GameScreen couch(GdxContext context, views.Renderers renderers) {
        CouchBoot boot = CouchBoot.start(renderers);
        return new GameScreen(context, boot.session(), boot.engine(), null, true);
    }

    private GameScreen(GdxContext context, GameSession session, GameEngine engine) {
        this(context, session, engine, null, false);
    }

    private GameScreen(GdxContext context, GameSession session, GameEngine engine, MatchBoot match,
                       boolean couchPlay) {
        this.context = context;
        this.session = session;
        this.engine = engine;
        this.match = match;
        this.couchPlay = couchPlay;
        this.environment = resolveEnvironment(session);

        this.background = new BackgroundRenderer(context.assets());
        background.load(environment);

        this.lawn = LawnGeometry.forEnvironment(environment);
        this.viewport = new FitViewport(viewWidth(), LawnGeometry.WORLD_HEIGHT, camera);

        this.showGrid = gridSetting(session);

        this.entities = new views.gdx.render.GameRenderer(
                context.assets(), context.sprites(), lawn, interpolator, environment);
        this.netLoop = match == null ? null : new views.gdx.bridge.NetLoopDriver(session, interpolator);
        this.loop = netLoop != null ? netLoop
                : new views.gdx.bridge.GameLoopDriver(engine, session, interpolator);
        this.loop.setGameSpeed(session.getPlayer() == null ? 1 : session.getPlayer().getGameSpeed());
        this.loop.setPaused(views.gdx.core.DebugFlags.START_PAUSED);

        this.narration = new views.gdx.renderers.GdxInGameRenderer(context.toasts(),
                this::onModelEvent);
        engine.setInGameRenderer(narration);

        this.cues = new views.gdx.core.AudioCues(context.audio());
        this.commands = new views.gdx.bridge.CommandBridge(
                match != null ? this::sendMatchCommand : engine::submitInGameCommand);
        attachAudio();
        this.lawnMusic = musicChain(session, environment, false);
        this.finalWaveMusic = musicChain(session, environment, true);
        this.lawnInput = new views.gdx.input.LawnInputProcessor(
                viewport, lawn, session, commands, tools, entities.collectibles());
        this.hud = new views.gdx.ui.GameHud(context.assets(), context.sprites(), session, tools,
                match == null ? null : match.faction(), couchPlay);
        this.cursor = new views.gdx.render.CursorRenderer(context.sprites(),
                new views.gdx.ui.UiArt(context.assets()), lawn);
        this.npc = new views.gdx.ui.NpcDialogueBox(context.assets(), context.sprites(), hud.stage());
        this.inputCheck = new InputCheck(context, session, engine, lawn, viewport, lawnInput, tools,
                hud, this::onModelEvent);
        // EVERY consumer of onModelEvent is constructed before installHudPanels/replayOpeningEvents,
        // and that ordering is load-bearing rather than tidy: replayOpeningEvents drains what the
        // model said during GameEngine.init() -- above all a mode's opening banner -- straight through
        // onModelEvent, so anything the fan-out touches that has not been built yet is null AT THAT
        // MOMENT, inside this constructor, before the screen exists. That is exactly how the pickup
        // flights crashed every level with a mode that announces itself (Night Ops, Deadline, Save Our
        // Seeds, Vasebreaker...) while every plain Egypt level, narrating nothing at init, worked.
        this.scorePopups = new views.gdx.ui.ScorePopups(hud.stage(), context.assets().skin(), viewport);
        this.pickups = new views.gdx.ui.PickupFlights(hud.stage(), context.assets().skin(),
                viewport, hud, lawn);
        connectPickups();

        installHudPanels();
        replayOpeningEvents();
        this.overlays = new views.gdx.ui.GameOverlays(context.assets(), hud.stage(),
                this::dismissObjective, this::resumePlay, this::restart, this::saveAndExit,
                this::leaveLevel);
        this.showcase = new Showcase(engine, session);
        this.harness = new MinigameHarness(session, engine, lawn, viewport, lawnInput, tools);

        openBoard();
    }

    // The two places a click has to be able to make a noise: the entity renderer plays impact and
    // death sounds, the command bridge plays the cue for the click itself.
    private void attachAudio() {
        entities.setAudio(context.audio());
        commands.setAudio(context.audio());
    }

    // A dropped reward has to know where it died (DeathEffects) and the input harness has to be able
    // to watch one fly. Both are back-references that can only be set once both ends exist.
    private void connectPickups() {
        pickups.setDeaths(entities.deaths());
        inputCheck.setPickups(pickups);
    }

    // The last thing the constructor does, and the only part of it that differs between a level and a
    // match.
    //
    // A level opens PAUSED behind its objective card: it is built and armed but the player has not
    // seen it yet, and a level that starts running while they are still reading what it wants is a
    // level they have already partly lost.
    //
    // A match cannot do that. The server is already ticking and the other player is already playing,
    // so a card that paused only this screen would hand them a free head start for as long as it
    // stayed up. What a match gets instead is its conversation -- see MatchPlay.
    private void openBoard() {
        if (match != null) {
            matchPlay = new MatchPlay(context, match, netLoop, narration, hud.stage());
            lawnInput.setFaction(match.faction());
        } else if (couchPlay) {
            // The mouse is the plant player's, so it is gated to their half exactly as it is over a
            // network. The keyboard is the other player's and is installed in show().
            couch = new CouchPlay(session, commands);
            lawnInput.setFaction(models.game.Faction.PLANTS);
        } else {
            openObjective();
        }
        centreOnLawn();
        Gdx.app.log("GameScreen", "world " + (int) background.totalWidth() + "x"
                + (int) background.height() + " | lawn " + lawn.describe());
    }

    // The fallback chain of track names for one half of a level. See AudioManager.playMusic: the first
    // name a file exists for wins.
    //
    // Built once rather than per frame. Both chains are handed to playMusic on EVERY render, which is
    // what makes the switch to the closing wave's theme need nothing watching for it -- the chain simply
    // resolves to a different name once the last wave launches. Allocating two arrays a frame for a call
    // that is usually a string compare would be silly.
    //
    // A mini-game has no world of its own -- MinigameFactory builds a Level with no template, the same
    // fact resolveEnvironment falls back on -- so it asks for its own track first and only then for
    // whatever world it happened to borrow a background from.
    private static String[] musicChain(GameSession session, EnvironmentType environment,
                                       boolean finalWave) {
        String minigame = isMinigame(session) ? views.gdx.core.AudioManager.MUSIC_MINIGAME : null;
        if (!finalWave) {
            return new String[] {
                minigame,
                views.gdx.core.AudioManager.lawnTrack(environment, false),
                views.gdx.core.AudioManager.MUSIC_LAWN,
            };
        }
        return new String[] {
            minigame,
            views.gdx.core.AudioManager.lawnTrack(environment, true),
            // Falls through to the ordinary lawn track, so a world with no closing theme of its own
            // simply keeps playing what it was playing rather than going silent for the last wave.
            views.gdx.core.AudioManager.lawnTrack(environment, false),
            views.gdx.core.AudioManager.MUSIC_LAWN,
        };
    }

    // Everything the level said before this screen existed, fed through the same fan-out as everything
    // it says afterwards.
    //
    // `GameEngine.init()` places a mode's plants and drains its opening banner, and it has always run
    // BEFORE the constructor gets to `setInGameRenderer` -- in a static helper on the real path, and
    // inside DevBoot on the dev one. So those sentences went to the app-level renderer, which has no
    // listener, and the screen's consumers never saw them. Toasts were unaffected, which is exactly why
    // this went unnoticed: the banner was visibly there.
    //
    // Replayed through onModelEvent ONLY, never through the toasts, because the toasts already showed
    // them once. See GdxInGameRenderer.drainBacklog.
    private void replayOpeningEvents() {
        if (!(context.renderers().inGame()
                instanceof views.gdx.renderers.GdxInGameRenderer opening)) {
            return;
        }
        java.util.List<String> backlog = opening.drainBacklog();
        if (views.gdx.core.DebugFlags.BOARD_COUNTS) {
            Gdx.app.log("Opening", "replaying " + backlog.size() + " event(s) said before this screen");
        }
        for (String message : backlog) {
            onModelEvent(message);
        }
    }

    // The HUD panels that need somewhere to post a command, so none of them can be built until the
    // engine and the bridge exist. Lifted out of the constructor, which is at Checkstyle's 50-line
    // ceiling and has now pushed it twice.
    private void installHudPanels() {
        // Cheat buttons synthesise the same command strings the prompt accepts, through the same door.
        hud.installCheats(engine::submitInGameCommand);
        // Coins and gems have no in-game command, so the lawn reaches them the way the menus do.
        hud.installCurrencyCheats(currencyCheat("coin"), currencyCheat("gem"));
        // The zombie spawner, through the same sink for the same reason.
        hud.installSpawner(engine::submitInGameCommand);
        // Beghouled's shop, through CommandBridge rather than the raw sink so the click gets its cue.
        hud.installUpgrades(commands::upgrade);
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

    // A level built by MinigameFactory carries no template. Same fact resolveEnvironment reads, asked
    // the other way round.
    private static boolean isMinigame(GameSession session) {
        return session != null && session.getLevel() != null
                && session.getLevel().getTemplate() == null;
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
        cameraBaseX = Math.max(minX, Math.min(maxX, lawnCentreX));
        cameraBaseY = LawnGeometry.WORLD_HEIGHT * 0.5f;
        camera.position.set(cameraBaseX, cameraBaseY, 0f);
        camera.update();
    }

    // The camera's kick, applied fresh from its resting place every frame.
    //
    // `delta` and not `animationDelta`: the shake belongs to the presentation rather than to the
    // simulation, so pausing on the frame a Gargantuar's hammer lands must not leave the board sitting
    // permanently askew with no way back.
    //
    // The zoom is what makes any of this legal -- the view frames the background's full height exactly,
    // so the margin CameraShake moves within is margin it manufactures for itself. See that class.
    private void applyShake(float delta) {
        shake.advance(delta);
        camera.zoom = shake.zoom();
        camera.position.set(cameraBaseX + shake.offsetX(viewport.getWorldWidth()),
                cameraBaseY + shake.offsetY(viewport.getWorldHeight()), 0f);
        camera.update();
        if (views.gdx.core.DebugFlags.SHAKE_CHECK && shake.isShaking()) {
            Gdx.app.log("CameraShake", String.format(java.util.Locale.ROOT,
                    "intensity %.4f  offset %+.2f, %+.2f  zoom %.4f", shake.intensity(),
                    camera.position.x - cameraBaseX, camera.position.y - cameraBaseY, camera.zoom));
        }
    }

    @Override
    public void render(float delta) {
        // Model first, then draw what it produced. Both the tick and the animation clock stop when the
        // loop is paused or the level is over, which is what freezes the board.
        loop.update(delta);
        // Straight after the tick, because a Meow Point rule fires in the same drain as the kill that
        // earned it: DeathEffects has just been told where that zombie died, and one frame later the
        // award would float off whichever zombie died next instead.
        scorePopups.watch(session, entities.deaths(), lawn);
        // Animations advance only while the game runs, so pausing freezes the board as the spec
        // requires. Renderers keep their own per-entity clocks; this is just the gate.
        float animationDelta = (!loop.isPaused() && loop.isPlaying()) ? delta : 0f;

        // Every frame, because that is what makes the level turn audible: playMusic is a string compare
        // once the right track is going, and the moment the last wave launches the chain resolves to a
        // different name and the theme changes by itself.
        context.audio().playMusic(onFinalWave() ? finalWaveMusic : lawnMusic);
        // Chewing is a STATE, not an event, so it cannot come off the narration like the rest. Paused
        // with the simulation via animationDelta: a frozen board should not keep eating.
        cues.tick(session, animationDelta);

        applyShake(delta);
        viewport.apply();
        batch.setProjectionMatrix(camera.combined);

        drawBoard(delta, animationDelta);

        // Over the sprites, because it is a warning about them: a zombie two columns from the house
        // has to be readable through the wash and more alarming for it. `delta`, not animationDelta --
        // a pulse frozen behind the pause panel would be a level that looks already lost.
        danger.draw(camera.combined, session, lawn, delta);

        // Under the grid but over the sprites: a wash on the tile the cursor is on, so it is obvious
        // where a click will land before it costs anything.
        views.gdx.map.GridPos hovered = lawnInput.hovered();
        if (hovered.isValid() && tools.isHolding()) {
            grid.highlight(camera.combined, lawn, hovered.col(), hovered.row(), hoverTint(hovered));
        }
        // Beghouled's picked-up tile. Its own colour and drawn whether or not the cursor is on it: it
        // is a SELECTION that persists between two clicks, not a hover, and a match-3 board with no mark
        // on the piece you have lifted is one where the second click is a guess.
        views.gdx.map.GridPos picked = lawnInput.swapSelection();
        if (picked != null && picked.isValid()) {
            grid.highlight(camera.combined, lawn, picked.col(), picked.row(), SWAP_TINT);
        }
        // The keyboard player's cursor. The plant player has a mouse pointer to look at; without this
        // the other person is pressing WASD at a board that gives them no sign of where they are.
        if (couch != null) {
            views.gdx.map.GridPos at = couch.cursor();
            grid.highlight(camera.combined, lawn, at.col(), at.row(), COUCH_TINT);
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
        // The pause panel follows the loop, and the result panel is raised on the one frame the level
        // stops being PLAYING. GameEngine has already settled quests, scored a scoring run and saved by
        // then -- this only reports it.
        overlays.setPauseVisible(loop.isPaused() && !overlays.isObjectiveVisible()
                && !overlays.isOutcomeVisible());
        reportEnding();
        if (matchPlay != null) {
            matchPlay.update(delta);
        }
        hud.update(loop.ticksRun());
        hud.render(delta);

        runFrameChecks();

        harness.tick();
        runFastForward();
        runOutcomeCheck();
        advanceShowcase();

        // Gameplay narration: the model queues events during the tick and the view drains them here.
        // Same seam the terminal build renders through, so nothing had to be added to the model.
        for (utils.Result event : session.drainEvents()) {
            // The same stream feeds two consumers. Explosions ignore anything that is not a detonation,
            // so neither has to know about the other.
            if (debugCounts) {
                Gdx.app.log("Event", event.message());
            }
            onModelEvent(event.message());
            context.toasts().show(event);
        }
    }

    // The last wave has launched. Levels count waves from 1, so this is true from the moment the final
    // one starts rather than when it is cleared -- which is the point at which the music should turn.
    private boolean onFinalWave() {
        models.game.Level level = session.getLevel();
        return level != null && level.getWaveCount() > 0
                && session.getCurrentWave() >= level.getWaveCount();
    }

    // Every view effect that is driven by the model's own narration, in one place.
    //
    // There are three call sites that hand events to the view (the in-game renderer, the screen's own
    // drain, and the input-check harness) and eight consumers, and before this they were wired
    // pairwise -- which is how the harness path ended up with explosions and no weather. Each consumer
    // ignores anything that is not its own sentence, so none of them needs to know about the others.
    private void onModelEvent(String message) {
        deliver("explosions", () -> entities.explosions().onEvent(message));
        deliver("weather", () -> entities.weather().onEvent(message));
        deliver("zombieActions", () -> entities.zombieActions().onEvent(message));
        deliver("botany", () -> entities.botany().onEvent(message));
        deliver("terrain", () -> entities.terrain().onEvent(message));
        deliver("impacts", () -> entities.impacts().onEvent(message));
        deliver("bossEffects", () -> entities.bossEffects().onEvent(message));
        deliver("deaths", () -> entities.deaths().onEvent(message));
        deliver("cameraShake", () -> shake.onEvent(message));
        deliver("audioCues", () -> cues.onEvent(message));
        // Flies a dropped reward from the corpse to the counter that just absorbed it -- every one of
        // these is already credited by the model, so this only makes the giving visible.
        deliver("pickupFlights", () -> pickups.onEvent(message));
        // Says nothing for the overwhelming majority of sentences -- see NpcLines.
        deliver("npcDialogue", () -> {
            views.gdx.ui.NpcDialogueBox.Speaker speaker = views.gdx.ui.NpcLines.speakerFor(message);
            if (speaker != null) {
                npc.say(speaker, message);
            }
        });
    }

    // One consumer's turn at a sentence, isolated from the other seven.
    //
    // Every one of these is a FLOURISH -- a puff of sand, a camera nudge, a coin flying to a counter.
    // Not one of them is load-bearing for the game being playable, and yet an exception in any of them
    // was killing the whole application: the fan-out runs on the render thread, so a throw walks out
    // through Lwjgl3Application.loop and the window closes with the player's level lost. That trade is
    // upside down, and it is what a null pickups field did on every Frostbite level 3.
    //
    // So a failing consumer is reported ONCE, by name, and the frame carries on. Once rather than every
    // frame because these fire dozens of times a second during a wave and an unthrottled stack trace is
    // its own denial of service. Same call the sprite layer makes for a malformed PAM: a missing effect
    // is a bug worth seeing in the log, a black window is not.
    private final java.util.Set<String> failedConsumers = new java.util.HashSet<>();

    private void deliver(String consumer, Runnable delivery) {
        try {
            delivery.run();
        } catch (RuntimeException e) {
            if (failedConsumers.add(consumer)) {
                Gdx.app.error("GameScreen", "event consumer \"" + consumer
                        + "\" failed and was muted for the rest of this level", e);
            }
        }
    }

    // Background, then the lawn's own markings, then everything standing on it.
    //
    // Three passes rather than one because the mode overlay is drawn with a ShapeRenderer, which cannot
    // be interleaved inside an open SpriteBatch. Splitting the batch is what buys the correct z-order:
    // a trip-wire, a no-plant line and a defended tile are paint ON the lawn, and drawn over the sprites
    // they would read as effects on whatever happens to be standing there.
    private void drawBoard(float delta, float animationDelta) {
        batch.begin();
        background.draw(batch);
        batch.end();

        modeOverlay.draw(camera.combined, session, lawn, animationDelta);

        batch.begin();
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
    }

    // What colour the tile under the cursor gets.
    //
    // The refusal itself stays in the model -- GameSession.removePlant asks the mode and answers with a
    // message. This only stops the view PROMISING something the model will refuse: a red "about to be
    // dug" wash over a Save Our Seeds plant is the view telling the player a lie the click then takes
    // back.
    private com.badlogic.gdx.graphics.Color hoverTint(views.gdx.map.GridPos hovered) {
        if (tools.tool() != views.gdx.input.ToolState.Tool.SHOVEL) {
            return HOVER_TINT;
        }
        models.game.gamemodes.GameMode mode = session.getMode();
        boolean diggable = mode == null || mode.isPlantRemovable(hovered.col(), hovered.row());
        return diggable ? SHOVEL_TINT : NO_DIG_TINT;
    }

    // An InputMultiplexer rather than setInputProcessor(lawnInput) directly: the HUD's Scene2D stage
    // has to get first refusal on every event, so clicking a seed card does not also plant on the tile
    // behind it. Whatever is added first wins, and the lawn is deliberately last.
    @Override
    public void show() {
        com.badlogic.gdx.InputMultiplexer input = new com.badlogic.gdx.InputMultiplexer();
        // In couch play the keyboard belongs to the ZOMBIE player and the mouse to the plant player,
        // so the single-player shortcuts step aside entirely: S, F and the digits are the shovel,
        // plant food and seed slots, and every one of them collides with WASD and 1-5. Two players
        // sharing one keyboard cannot also share its bindings.
        input.addProcessor(couch != null ? couch.input() : new KeyboardTools());
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
            // Escape disarms the cursor as well as pausing -- see the pause branch below.
            if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
                tools.clear();
            }
            // The cheat panel is a debug tool, so it is gated on the setting that says so. Without the
            // gate a stray C during a real match hands the player a "Nuke Everything" button.
            if (keycode == com.badlogic.gdx.Input.Keys.C) {
                return debugPanel(hud::toggleCheats, "cheats");
            }
            // The zombie spawner, gated on the same setting and for the same reason: it can put a
            // Gargantuar in front of the house.
            if (keycode == com.badlogic.gdx.Input.Keys.Z) {
                return debugPanel(hud::toggleSpawner, "the zombie spawner");
            }
            // Pausing stops the accumulator, so the model AND every animation clock freeze together --
            // a paused board that keeps waving its leaves does not read as paused.
            //
            // ESCAPE is here because it is the key every player reaches for first, and it was the one
            // key that did not pause. It keeps its old job of disarming the cursor (above) rather than
            // trading it away, and does both on the SAME press: a player holding a seed who hits Escape
            // wants out of the seed as well as into the menu, and making them press it twice reads as
            // the key not working. Coming back from a pause with a live seed stuck to the cursor is a
            // mis-click waiting to happen anyway.
            //
            // Guarded on the same two panels the pause overlay itself is (see render): the objective
            // card and the result panel already hold the loop paused for their own reasons, so a toggle
            // underneath either of them would unpause a board the player can neither see nor play. That
            // guard is new for P and SPACE too, which could do exactly that.
            if (keycode == com.badlogic.gdx.Input.Keys.P
                    || keycode == com.badlogic.gdx.Input.Keys.SPACE
                    || keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
                if (!overlays.isObjectiveVisible() && !overlays.isOutcomeVisible()) {
                    loop.togglePause();
                }
                return true;
            }
            if (keycode >= com.badlogic.gdx.Input.Keys.NUM_1
                    && keycode <= com.badlogic.gdx.Input.Keys.NUM_9) {
                selectSeedSlot(keycode - com.badlogic.gdx.Input.Keys.NUM_1);
                return true;
            }
            return false;
        }

        // A debug panel is only reachable when the setting that calls it debug is on -- without the
        // gate a stray key during a real match hands the player a "Nuke Everything" button, or a
        // Gargantuar.
        private boolean debugPanel(Runnable toggle, String what) {
            if (debugMode()) {
                toggle.run();
            } else {
                context.toasts().info("Turn on Debug mode in Settings to use " + what + ".");
            }
            return true;
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
    // The countdown-triggered harness checks, fired AFTER the HUD has drawn and never before: Scene2D
    // positions nothing until its first layout pass, so a check that runs on frame zero finds every
    // widget at (0, 0) with no size and concludes -- wrongly -- that clicks miss them.
    //
    // Lifted out of render(), which hit Checkstyle's 50-line method ceiling the moment a third one was
    // added. Each is off unless its flag is set, so a normal frame does three int compares.
    private void runFrameChecks() {
        if (inputCheckFrames > 0 && --inputCheckFrames == 0) {
            inputCheck.run();
        }
        if (pickupCheckFrames > 0 && --pickupCheckFrames == 0) {
            inputCheck.runPickupCheck();
        }
        if (spawnerCheckFrames > 0 && --spawnerCheckFrames == 0) {
            inputCheck.runSpawnerCheck(views.gdx.core.DebugFlags.SPAWNER_CHECK);
        }
    }

    private int inputCheckFrames = views.gdx.core.DebugFlags.INPUT_CHECK ? 3 : 0;

    // Frame 20, not 3: the spawner's Window has to have been laid out before its SelectBoxes hold
    // anything selectable, and the screenshot wants the zombie to have taken a step.
    // Frame 25: late enough for the HUD's counters to have been laid out (a flight aimed at a widget
    // with no position yet lands at the stage's corner), early enough to leave the flights and the
    // floating text still on screen when a 70-frame smoke run ends.
    private int pickupCheckFrames = views.gdx.core.DebugFlags.PICKUP_CHECK ? 25 : 0;

    private int spawnerCheckFrames =
            views.gdx.core.DebugFlags.SPAWNER_CHECK.isEmpty() ? 0 : 20;

    // Built in the constructor, not here: field initialisers run before the constructor body, so
    // engine and session are still null at this point.
    private final Showcase showcase;
    private int showcaseFrames = views.gdx.core.DebugFlags.SHOWCASE ? 5 : 0;
    private int showcaseFeedFrames;
    private int showcaseSunFrames;
    private int showcaseShedFrames;

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
            showcaseShedFrames = 60;
        }
        // Deferred for a reason that is easy to miss: ZombieRenderer spots a destroyed armour by the
        // layer VANISHING between two frames, so the armoured zombies have to be drawn intact at least
        // once before they are shot. Breaking the cone in the same call that spawns the Conehead
        // records the bare state as its first sighting and nothing is ever thrown.
        if (showcaseShedFrames > 0 && --showcaseShedFrames == 0) {
            showcase.shedArmour();
        }
    }

    // -Dpvz.showOutcome=win|lose: raises the end-of-level panel, once, without playing to the end.
    //
    // Later than the setup flags (-Dpvz.spawn at 20, the fast-forward at 35, -Dpvz.run at 50), so the
    // panel reports on a board something has actually happened to. It was 30, which raised the win panel
    // BEFORE any of them and made the scoring game's breakdown come up permanently empty -- the panel
    // was right and the frame number was not, which is the same shape of trap the shake and the glow
    // were.
    private static final int OUTCOME_FRAME = 60;

    private int outcomeCheckFrames;

    private void runOutcomeCheck() {
        String wanted = views.gdx.core.DebugFlags.SHOW_OUTCOME;
        if (wanted.isEmpty() || ++outcomeCheckFrames != OUTCOME_FRAME) {
            return;
        }
        boolean won = "win".equalsIgnoreCase(wanted);
        raiseOutcome(won);
        Gdx.app.log("OutcomeCheck", "raised the " + (won ? "win" : "loss") + " panel");
    }

    // The end of a level: the panel, and the sting that goes with it.
    //
    // One method because BOTH callers must do the same thing -- the real end of the level and
    // -Dpvz.showOutcome. The harness has always claimed to make "the same call the real end makes", and
    // when the sting was added to the real path alone that stopped being true: the flag raised a silent
    // panel and there was no way to tell from it whether a real win would have been silent too.
    // ---- the networked match ---------------------------------------------------------------------

    // The conversation half of a match -- the six packets it speaks in, the reaction bar and popup,
    // and who won. Null on a single-player lawn. See MatchPlay, which was lifted out of this class
    // for the reason InputCheck was: a 500-statement ceiling, and a versus match pushing it over.
    private MatchPlay matchPlay;

    // The CommandBridge sink in a match. Indirect through the field rather than bound to MatchPlay
    // directly, because the bridge is constructed before MatchPlay is -- and a click cannot arrive in
    // between, since both happen inside this constructor with no frame drawn between them.
    private boolean sendMatchCommand(String command) {
        return matchPlay != null && matchPlay.sendCommand(command);
    }

    // Raises the result panel on the one frame the board stops being live, so it and its sting arrive
    // exactly once.
    //
    // In a match the source of truth is MatchOver, not the session: this client's board is never
    // simulated, so its GameState sits at PLAYING for the whole match and would report a loss to both
    // players.
    private void reportEnding() {
        if (loop.isPlaying() || overlays.isOutcomeVisible()) {
            return;
        }
        boolean won = session.getState() == models.game.GameState.WON;
        if (matchPlay != null) {
            matchPlay.raiseOutcome(overlays);
        } else if (couch != null) {
            // Neither person at this keyboard is "you", so the panel names the winning SIDE.
            couch.raiseOutcome(overlays, won);
        } else {
            raiseOutcome(won);
        }
    }

    private void raiseOutcome(boolean won) {
        context.audio().play(won ? views.gdx.core.AudioManager.SFX_WIN
                : views.gdx.core.AudioManager.SFX_LOSE);
        // The Meow Point breakdown, or null on every level that is not the scoring game. Read straight
        // off the mode rather than parsed back out of the toast GameEngine already sent: the engine has
        // settled the run by the time this frame runs, so the manager's numbers are final and are the
        // same source the terminal card prints from.
        models.game.scoring.MeowPointManager points =
                session.getMode() instanceof models.game.gamemodes.ScoringMode scoring
                        ? scoring.getMeowPoints() : null;
        overlays.showOutcome(won, won
                ? "The lawn is safe. For now."
                : "They got past you this time. Try a different loadout.", points);
    }

    // -Dpvz.fastForward=N: N game ticks in one frame, once, a moment after the board opens.
    //
    // Late enough that the objective card has been dismissed and the loop is actually running -- ticks
    // submitted while the level is still paused behind it are simply refused.
    private static final int FAST_FORWARD_FRAME = 35;

    private int fastForwardFrames;

    private void runFastForward() {
        if (views.gdx.core.DebugFlags.FAST_FORWARD < 1) {
            return;
        }
        if (++fastForwardFrames != FAST_FORWARD_FRAME) {
            return;
        }
        int ticks = views.gdx.core.DebugFlags.FAST_FORWARD;
        engine.submitInGameCommand("advance time -t " + ticks + " ticks");
        int rushed = rushWaves();
        Gdx.app.log("FastForward", "ran " + ticks + " ticks and " + rushed
                + " rushed waves; now on wave " + session.getCurrentWave()
                + " of " + totalWaves());
    }

    // -Dpvz.rushWaves=N, on top of the fast-forward: N rounds of "clear the board, let the next wave
    // come".
    //
    // Waves after the first are gated by HEALTH, not by time -- the next one launches once 75% of the
    // current one's HP is gone -- so no amount of `advance time` alone reaches a late wave. That
    // matters because Egypt's tornado fires on the FINAL wave and nowhere else, which made it the one
    // world effect no capture could get to.
    // Long enough for the wave to finish TRICKLING in: a wave is not eligible to be replaced while it
    // still has pending spawns, so nuking the board and immediately asking for the next one changes
    // nothing. This is the wait for the rest of the wave to walk on so the nuke can catch it.
    private static final int RUSH_TICKS_PER_WAVE = 150;

    // Stops the moment the FINAL wave has launched, and returns how many rounds it took.
    //
    // Rushing past it would be self-defeating: the next nuke clears the last wave, the level ends, and
    // the loop stops advancing the animation clock -- so the tornado this exists to capture is raised
    // on the very frame everything freezes, and never fades in. Stopping on arrival leaves the storm
    // playing over a board that is still running.
    private int rushWaves() {
        int rushed = 0;
        for (int i = 0; i < views.gdx.core.DebugFlags.RUSH_WAVES; i++) {
            if (session.getCurrentWave() >= totalWaves()) {
                break;
            }
            engine.submitInGameCommand("release the nuke");
            engine.submitInGameCommand("advance time -t " + RUSH_TICKS_PER_WAVE + " ticks");
            rushed++;
        }
        return rushed;
    }

    private int totalWaves() {
        if (session.getLevel() == null || session.getLevel().getWaves() == null) {
            return Integer.MAX_VALUE;   // no wave list: never claim to have reached the end of it
        }
        return session.getLevel().getWaves().length;
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

    // ---- the five things the overlays can do ----

    // What this level wants, before it starts. The wave count is the objective for every standard
    // level; a mode with its own goal says so itself through describeObjective. Both live in
    // LevelObjective -- lifted out when this class hit Checkstyle's 500-NCSS ceiling for the third
    // time, and a better home for it anyway: what a level is for is a question about the session.
    private void openObjective() {
        // The showcase drives scripted actions and needs a running board; -Dpvz.skipIntro does the same
        // for a screenshot run. Both would otherwise capture this card instead of the lawn.
        if (views.gdx.core.DebugFlags.SKIP_INTRO || views.gdx.core.DebugFlags.SHOWCASE) {
            return;
        }
        loop.setPaused(true);
        overlays.showObjective(levelName(), objectiveText(), openQuests(), session.getPlayer());
        greetTheLevel();
    }

    // The quests still outstanding, most important first.
    //
    // Read the same way the Travel Log reads them, through QuestSystem, so the card and the log rank and
    // flag them identically -- and built fresh rather than cached, because a quest finished last level
    // must not still be on this level's card.
    private java.util.List<models.quests.Quest> openQuests() {
        models.user.Profile profile = session.getPlayer();
        if (profile == null) {
            return java.util.List.of();
        }
        java.util.List<models.quests.Quest> open = new java.util.ArrayList<>();
        for (models.quests.Quest quest
                : new controllers.systems.game.QuestSystem().getSortedQuestsForLog(profile)) {
            if (!quest.isComplete()) {
                open.add(quest);
            }
        }
        return open;
    }

    // Somebody says hello, unless somebody is already talking.
    //
    // The guard is the whole of the wiring: replayOpeningEvents runs before this and delivers a special
    // mode's opening banner, which Penny is already saying. say() REPLACES rather than queues, so
    // without the check a Vasebreaker board would explain the weather instead of Vasebreaker.
    //
    // Held rather than timed out: the board is paused behind the objective card, so a nine-second dwell
    // would expire while the player is still reading the card it came in with. dismissObjective clears
    // it at the same moment the level starts.
    private void greetTheLevel() {
        if (npc.isVisible()) {
            // A mode's rules banner got here first. Keep it, and stop its clock -- its dwell is
            // wall-clock and the board is paused behind the card, so it would fade out unread.
            npc.holdCurrent();
            return;
        }
        if (session.getLevel() == null || session.getLevel().getTemplate() == null) {
            return;
        }
        models.templates.LevelTemplate template = session.getLevel().getTemplate();
        views.gdx.ui.NpcGreetings.Greeting greeting = views.gdx.ui.NpcGreetings.forLevel(
                models.game.EnvironmentType.fromChapter(template.getChapter()),
                template.getLevelNumber());
        if (greeting != null) {
            npc.hold(greeting.speaker(), greeting.line());
        }
    }

    private String levelName() {
        if (session.getLevel() != null && session.getLevel().getTemplate() != null) {
            return session.getLevel().getTemplate().getName();
        }
        return "The Lawn";
    }

    private String objectiveText() {
        return views.gdx.ui.LevelObjective.of(session);
    }

    private void dismissObjective() {
        overlays.hideObjective();
        // The greeting came in with the card and goes out with it: it is a pre-level line, and leaving
        // it standing over a live lawn turns it into something the player has to click away.
        npc.hide();
        loop.setPaused(false);
    }

    private void resumePlay() {
        overlays.setPauseVisible(false);
        loop.setPaused(false);
    }

    // Replays the level with the same loadout. A brand new GameSession, because a played board cannot
    // be rewound -- plants are gone, mowers are spent and the wave system has advanced. The seed names
    // are carried across so the player does not re-pick eight cards to retry.
    private void restart() {
        if (match != null) {
            // There is nothing here to replay. The board belongs to the server and the opponent has
            // gone back to the lobby; a rematch is arranged there, by asking them again.
            context.toasts().error("Ask them for a rematch from the lobby.");
            return;
        }
        if (couchPlay) {
            // Not a limitation worth working around: restart() reuses the LEVEL, and therefore the
            // same mode object, whose onStart is guarded against running twice -- a replayed versus
            // board would come up with no brains and no sun makers. Start a fresh one instead.
            context.toasts().error("Start a new couch match from the Multiplayer menu.");
            return;
        }
        models.game.Level level = session.getLevel();
        models.user.Profile profile = session.getPlayer();
        if (level == null || profile == null) {
            context.toasts().error("Nothing to restart.");
            return;
        }
        // Copied packet by packet rather than name by name: an imitated packet is a second copy of a
        // plant already on the bar, and rebuilding the loadout from names alone turned it into an
        // ordinary duplicate that "remove the Imitater" could no longer find.
        java.util.List<models.game.SeedPacket> loadout =
                new java.util.ArrayList<>(session.getSelectedSeeds());

        GameSession fresh = new GameSession(profile, level);
        for (models.game.SeedPacket packet : loadout) {
            models.templates.PlantTemplate template =
                    utils.registry.PlantRegistry.getInstance().getTemplateByName(packet.getPlantType());
            if (template != null) {
                fresh.addSeed(new models.game.SeedPacket(packet.getPlantType(),
                        (int) Math.round(template.getRecharge()), packet.isImitated()));
            }
        }
        context.appSession().setCurrentGameSession(fresh);
        // The menu does not change, so sync() would do nothing -- the screen has to be replaced
        // explicitly. This GameScreen is disposed by the swap.
        context.screens().reopen();
    }

    // Quitting mid-level is a forfeit, and the engine treats it as one: quests settle, a scoring run is
    // scored, the profile is saved. Doing less than that would let a losing level be escaped for free.
    private void saveAndExit() {
        if (matchPlay != null) {
            // Leaving a match is a forfeit and the SERVER decides that, not this client -- calling
            // abandonLevel here would end a board this machine does not own and quietly leave the
            // opponent playing against nobody. See MatchPlay.leave.
            matchPlay.leave();
            leaveLevel();
            return;
        }
        engine.abandonLevel();
        leaveLevel();
    }

    // Back to the level map, with the finished session let go. A match goes back to the lobby instead:
    // the adventure map is not where it came from and not where a rematch is arranged.
    private void leaveLevel() {
        if (matchPlay != null) {
            matchPlay.release();
        }
        context.appSession().setCurrentGameSession(null);
        context.appSession().setCurrentMenu(match != null || couchPlay
                ? controllers.engine.MenuType.ONLINE_MENU
                : controllers.engine.MenuType.PLAY_MENU);
    }

    // -Dpvz.grid=1/0 overrides the profile in both directions, so calibration work does not require
    // signing in and changing a setting first.
    private static boolean gridSetting(GameSession session) {
        String override = System.getProperty("pvz.grid");
        if (override != null && !override.isBlank()) {
            return !"0".equals(override.trim());
        }
        return session.getPlayer() != null && session.getPlayer().isShowGrid();
    }

    // A debug currency top-up, or null when Debug Mode is off -- which is what makes the button not
    // exist rather than exist and refuse. Constructs the Command directly for the same reason
    // MenuScreen does: `menu cheat add` is a PLAY_MENU command and the session is IN_GAME here, so the
    // router would refuse the string. The rule still lives in CheatAddCommand.
    private java.util.function.IntConsumer currencyCheat(String currency) {
        if (!debugMode()) {
            return null;
        }
        return amount -> {
            if (session.getPlayer() == null) {
                return;
            }
            new controllers.commands.playmenu.CheatAddCommand(currency, amount, session.getPlayer(),
                    context.renderers().playMenu()).execute();
        };
    }

    private boolean debugMode() {
        return session.getPlayer() != null && session.getPlayer().isDebugMode();
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    @Override
    public void dispose() {
        if (matchPlay != null) {
            matchPlay.release();
        }
        batch.dispose();
        grid.dispose();
        danger.dispose();
        hud.dispose();
    }
}
