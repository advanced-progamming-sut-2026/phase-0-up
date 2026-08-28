package views.gdx.bridge;

import models.game.GameSession;
import net.packets.MatchSnapshot;
import utils.Constants;

import java.util.concurrent.atomic.AtomicReference;

// The other half of LoopDriver: a board that is watched rather than run.
//
// GameLoopDriver's job is to call advanceOneTick() at exactly 10 Hz no matter what the frame rate is.
// This one never advances anything. Snapshots arrive from the server at that same 10 Hz -- not by
// coincidence, it is the rate the whole game is written in -- and each one is applied to the mirror
// board and then smoothed across the frames until the next arrives, by the SAME EntityInterpolator
// Phase 2 wrote for the single-player build.
//
// ## alpha() is a clock, not an accumulator
//
// The single-player driver knows exactly how much simulated time it has banked, so its alpha falls out
// of the accumulator. Here there is nothing to bank: a snapshot has either arrived or it has not. So
// alpha is how long it has been since the last one, as a fraction of a tick, clamped at 1 -- which
// makes a late packet hold the board at its destination instead of overshooting past it.
//
// ## Pause and game speed do not exist here
//
// Both are single-player ideas. One player pausing cannot stop a shared simulation, and one player
// running at speed 3 would be watching a different match. setPaused is accepted and ignored on
// purpose rather than throwing: GameScreen calls it from the menu, the settings and the outcome
// panel, and it should not have to know which kind of board it is showing.
public final class NetLoopDriver implements LoopDriver {

    private static final float TICK_SECONDS = 1f / Constants.TICKS_PER_SECOND;

    private final GameSession session;
    private final EntityInterpolator interpolator;
    private final SnapshotReconciler reconciler;

    // The latest board the server has described, waiting to be applied.
    //
    // One reference, replaced whole rather than queued: if two snapshots arrive between two frames the
    // newer one is simply the truth and the older one is of no interest. A queue would let the client
    // fall behind and stay behind, which is the one thing a mirror must never do.
    //
    // Atomic because it costs nothing and the guarantee is worth having: PushRouter posts handlers to
    // the render thread today, so both ends of this are the same thread, but that is PushRouter's
    // decision and not something this class should silently depend on.
    private final AtomicReference<MatchSnapshot> pending = new AtomicReference<>();

    private float sinceSnapshot;
    private long ticksRun;
    private boolean playing = true;

    public NetLoopDriver(GameSession session, EntityInterpolator interpolator) {
        this.session = session;
        this.interpolator = interpolator;
        this.reconciler = new SnapshotReconciler(session);
        this.reconciler.clearMowers();
        interpolator.prime(session);
    }

    // Called from the network thread. Does no model work -- see the field comment.
    public void onSnapshot(MatchSnapshot snapshot) {
        if (snapshot != null) {
            pending.set(snapshot);
        }
    }

    // The match is over. The board stops where it is rather than being torn down: the outcome panel
    // is raised on top of the final frame, and a blank lawn behind it would read as a crash.
    public void onMatchOver() {
        playing = false;
    }

    @Override
    public void update(float deltaSeconds) {
        MatchSnapshot snapshot = pending.getAndSet(null);
        if (snapshot != null) {
            // The same three calls the single-player driver makes around advanceOneTick(). What sits
            // between beginTick and endTick is the only difference between the two drivers.
            interpolator.beginTick();
            reconciler.apply(snapshot);
            interpolator.endTick(session);
            ticksRun = snapshot.tick();
            sinceSnapshot = 0f;
        }
        sinceSnapshot += deltaSeconds;
    }

    @Override
    public float alpha() {
        return Math.min(1f, sinceSnapshot / TICK_SECONDS);
    }

    @Override
    public boolean isPlaying() {
        return playing;
    }

    // Never paused: there is no state in which this board is frozen and the server's is not.
    @Override
    public boolean isPaused() {
        return false;
    }

    @Override
    public void setPaused(boolean paused) {
    }

    @Override
    public void togglePause() {
    }

    @Override
    public void setGameSpeed(int gameSpeed) {
    }

    @Override
    public int gameSpeed() {
        return 1;
    }

    @Override
    public long ticksRun() {
        return ticksRun;
    }

    public SnapshotReconciler reconciler() {
        return reconciler;
    }
}
