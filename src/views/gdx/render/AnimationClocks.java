package views.gdx.render;

import java.util.IdentityHashMap;
import java.util.Map;

// Gives every entity its own animation time, restarted whenever it changes clip.
//
// Two things go wrong with one shared stateTime for the whole board:
//
//  * every zombie walks in perfect lockstep, because they all sample the same phase of the same
//    animation. A row of identical zombies stepping as one is very obvious.
//  * changing clip does not restart it. A zombie that stops to bite samples "eat" at whatever the
//    global clock happens to read, so it starts mid-chew; a Peashooter's "attack" begins partway
//    through the shot. Short one-shot animations effectively never play from the beginning, which
//    reads as "there is no eating animation".
//
// Keyed by identity, like EntityInterpolator and for the same reason: projectiles all share id 0.
public final class AnimationClocks {

    private static final class Clock {
        String clip;
        float time;
        boolean seenThisFrame;
    }

    private final Map<Object, Clock> clocks = new IdentityHashMap<>();

    // Advances and returns this entity's time in the given clip. Call once per entity per frame,
    // during its draw. A changed clip resets the clock to 0, so the new animation starts at frame 0.
    public float advance(Object key, String clip, float delta) {
        Clock clock = clocks.get(key);
        if (clock == null) {
            clock = new Clock();
            clock.clip = clip;
            clocks.put(key, clock);
        }
        if (clip != null && !clip.equals(clock.clip)) {
            if (views.gdx.core.DebugFlags.BOARD_COUNTS) {
                // A clip that changes every frame resets this clock every frame, which freezes the
                // animation on frame 0 and looks like "there is no animation" or a flicker. Seeing the
                // churn is the only practical way to catch it.
                com.badlogic.gdx.Gdx.app.log("ClipChange", key.getClass().getSimpleName()
                        + "#" + Integer.toHexString(System.identityHashCode(key))
                        + " " + clock.clip + " -> " + clip);
            }
            clock.clip = clip;
            clock.time = 0f;
        }
        clock.time += delta;
        clock.seenThisFrame = true;
        return clock.time;
    }

    // Drops clocks for anything no longer drawn, so a long level cannot accumulate entries for every
    // zombie that ever walked on. Call once at the end of the frame.
    public void sweep() {
        clocks.entrySet().removeIf(entry -> {
            if (!entry.getValue().seenThisFrame) {
                return true;
            }
            entry.getValue().seenThisFrame = false;
            return false;
        });
    }

    public int trackedCount() {
        return clocks.size();
    }

    // Current clip and elapsed time for one entity, for diagnostics.
    public String describe(Object key) {
        Clock clock = clocks.get(key);
        return clock == null ? "(untracked)"
                : clock.clip + "@" + String.format("%.2f", clock.time) + "s";
    }
}
