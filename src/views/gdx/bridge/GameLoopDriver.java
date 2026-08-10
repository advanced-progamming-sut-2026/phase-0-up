package views.gdx.bridge;

import controllers.engine.GameEngine;
import models.game.GameSession;
import models.game.GameState;
import utils.Constants;

// Drives the terminal game's tick from LibGDX's render loop.
//
// The simulation is a fixed 10 Hz and every rate in the game -- zombie speed, seed cooldowns, sun
// intervals, ice melt -- is expressed in those ticks. So the tick rate must NOT follow the frame rate:
// a 144 Hz monitor would otherwise play the game fourteen times faster. An accumulator runs the model
// at exactly TICKS_PER_SECOND regardless of how often render() is called, and the leftover fraction
// becomes the interpolation alpha.
//
// GameEngine.advanceOneTick() is used verbatim -- same systems, same order, same win/loss evaluation
// as the terminal build. Nothing about the simulation is re-implemented here.
public final class GameLoopDriver {

    private static final float TICK_SECONDS = 1f / Constants.TICKS_PER_SECOND;

    // Ceiling on how much time one frame may consume. Without it, a long stall (a window drag, a
    // breakpoint, an atlas load) banks seconds of debt and the game fast-forwards violently to catch
    // up -- usually straight into a loss. Dropping that time is the right trade.
    private static final float MAX_FRAME_SECONDS = 0.25f;

    private final GameEngine engine;
    private final GameSession session;
    private final EntityInterpolator interpolator;

    private float accumulator;
    private int gameSpeed = 1;      // the Settings "Game Speed" 1..3
    private boolean paused;
    private long ticksRun;

    public GameLoopDriver(GameEngine engine, GameSession session, EntityInterpolator interpolator) {
        this.engine = engine;
        this.session = session;
        this.interpolator = interpolator;
        interpolator.prime(session);
    }

    public void update(float deltaSeconds) {
        if (paused || !isPlaying()) {
            // Deliberately does not touch the accumulator: on resume the game continues from exactly
            // where it stopped, and because stateTime stops advancing too, animations freeze with it --
            // which is what the spec means by "all entities and animations must freeze".
            return;
        }

        accumulator += Math.min(deltaSeconds, MAX_FRAME_SECONDS) * gameSpeed;

        while (accumulator >= TICK_SECONDS) {
            interpolator.beginTick();
            engine.advanceOneTick();
            interpolator.endTick(session);
            ticksRun++;
            accumulator -= TICK_SECONDS;

            // The level can end mid-batch. Stop immediately rather than ticking a finished board --
            // GameEngine.advanceTime does the same thing for the same reason.
            if (!isPlaying()) {
                accumulator = 0f;
                break;
            }
        }
    }

    // How far through the current tick we are, 0..1. Renderers blend positions with this.
    public float alpha() {
        return Math.min(1f, accumulator / TICK_SECONDS);
    }

    public boolean isPlaying() {
        return session.getState() == GameState.PLAYING;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void togglePause() {
        paused = !paused;
    }

    // 1..3, per the phase-2 Settings menu. Clamped rather than rejected: a bad persisted value should
    // not make the game unplayable.
    public void setGameSpeed(int gameSpeed) {
        this.gameSpeed = Math.max(1, Math.min(3, gameSpeed));
    }

    public int gameSpeed() {
        return gameSpeed;
    }

    public long ticksRun() {
        return ticksRun;
    }
}
