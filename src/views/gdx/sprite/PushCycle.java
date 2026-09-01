package views.gdx.sprite;

import com.badlogic.gdx.math.Rectangle;

// Shoving: how far along its cycle the thing a zombie is pushing has been moved, at any moment.
//
// Sister to WalkCycle, and it hands back the same kind of answer -- a SHAPE, p(t) normalised to [0, 1],
// which the caller multiplies by how far the MODEL travels in one cycle. What differs is the part it
// reads and what it does with the reading.
//
// ## The cycle is push, walk up, push
//
// A heavy object does not glide along in front of the zombie at a fixed distance; that is the thing
// this class exists to stop. It goes forward in SHOVES. The arms come through, the block slides, the
// arms draw back and the block stays exactly where it was put -- and the zombie closes the gap by
// walking up to it. Then it happens again.
//
// So the curve here is the RUNNING MAXIMUM of the pushing hand's forward position. The block advances
// with the hand while the hand is going forward, and holds while the hand comes back, because a block
// on the ground has no reason to follow a hand that has let go of it. That single choice is the whole
// mechanic: a monotonic curve that is flat for part of the cycle and steep for the rest.
//
// The caller adds `stride * (p(t) - t/T)`, exactly as it does for foot planting. The straight-line term
// is what makes the hold a real hold: during the flat stretch the model's own advance is subtracted off
// and the block stands still on screen while the zombie walks into it.
//
// Three properties fall out, and all three matter -- the same three WalkCycle relies on:
//
//   * p(0) = 0 and p(T) = 1, so a looping clip never jumps at the seam;
//   * it never accumulates, so the block cannot drift away from the zombie;
//   * the amplitude follows the model's real speed, so a chilled pusher shoves proportionally less.
//
// Both pushers share one skeleton -- ZOMBIE_80S_ARCADE's part list is the Troglobite's, down to the
// misspelled `zombie_troglobite_hand_oute_push` -- so one candidate list covers them, push hand first.
public final class PushCycle {

    private static final String[] HAND_PARTS = {
            "zombie_troglobite_hand_oute_push",
            "zombie_troglobite_hand_outer",
            "zombie_hand_outer_02",
            "zombie_hand_outer_01",
    };

    // Below this the hand is not really shoving and normalising would turn measurement noise into a
    // stutter. In animation units, like WalkCycle's MIN_TRAVEL.
    private static final float MIN_SWING = 3f;

    private final float[] profile;
    private final float duration;

    private PushCycle(float[] profile, float duration) {
        this.profile = profile;
        this.duration = duration;
    }

    // Null when this clip cannot drive a shove: no hand posed in it, too few frames, or a hand that
    // barely moves. Callers fall back to a fixed offset, which is what they did before this existed.
    public static PushCycle of(EntitySprite sprite, String clip) {
        if (sprite == null || clip == null) {
            return null;
        }
        float duration = sprite.clipDuration(clip);
        if (duration <= 0f) {
            return null;
        }
        Rectangle[] frames = framesOf(sprite, clip);
        if (frames == null || frames.length < 3) {
            return null;
        }

        float forward = forwardSign(sprite, clip);
        float[] reach = new float[frames.length];
        float previous = 0f;
        for (int i = 0; i < frames.length; i++) {
            // The hand's CENTRE, not its left edge: a hand that rotates as it pushes changes the width
            // of its own box, and the edge would move with that as well as with the thrust.
            float value = frames[i] == null ? previous : frames[i].x + frames[i].width / 2f;
            previous = value;
            reach[i] = forward * value;
        }

        // The running maximum: how far forward the hand has EVER been by this frame, which is where it
        // has left the block. Flat wherever the hand is on its way back.
        float[] pushed = new float[reach.length];
        pushed[0] = reach[0];
        for (int i = 1; i < reach.length; i++) {
            pushed[i] = Math.max(pushed[i - 1], reach[i]);
        }

        float travel = pushed[pushed.length - 1] - pushed[0];
        if (travel < MIN_SWING) {
            return null;
        }
        float[] profile = new float[pushed.length];
        for (int i = 0; i < pushed.length; i++) {
            profile[i] = (pushed[i] - pushed[0]) / travel;
        }
        return new PushCycle(profile, duration);
    }

    // Which way is "forward" in this animation's own coordinates.
    //
    // Derived rather than assumed, from the part WalkCycle already trusts: `ground_swatch` marks where
    // the ground was when the step began and slides BACKWARDS as the body advances over it, so the
    // direction the body is going is the opposite of the direction the swatch travels. Zombie art is
    // authored walking left, which makes that -1 in practice -- and that is the fallback when the clip
    // poses no swatch, so an animation that breaks the convention still gets the right answer from the
    // swatch when it has one.
    private static float forwardSign(EntitySprite sprite, String clip) {
        Rectangle[] swatch = sprite.partBoundsByFrame(clip, WalkCycle.GROUND_SWATCH);
        if (swatch == null || swatch.length < 2) {
            return -1f;
        }
        Float first = null;
        Float last = null;
        for (Rectangle frame : swatch) {
            if (frame == null) {
                continue;
            }
            if (first == null) {
                first = frame.x;
            }
            last = frame.x;
        }
        if (first == null || last.equals(first)) {
            return -1f;
        }
        return last > first ? -1f : 1f;
    }

    private static Rectangle[] framesOf(EntitySprite sprite, String clip) {
        for (String part : HAND_PARTS) {
            if (!sprite.hasPart(part)) {
                continue;
            }
            Rectangle[] frames = sprite.partBoundsByFrame(clip, part);
            if (frames != null && frames.length >= 3) {
                return frames;
            }
        }
        return null;
    }

    // How far ahead of its straight-line position the pushed object should be drawn, as a fraction of
    // one cycle's travel. Positive through a shove -- the block has been sent on ahead of where a
    // constant glide would have it -- and falling back through the hold as the zombie catches up.
    public float lead(float stateTime) {
        if (duration <= 0f) {
            return 0f;
        }
        float phase = Math.min(Math.max(stateTime / duration, 0f), 1f);
        return sample(phase) - phase;
    }

    // Linear between frames, for the same reason WalkCycle interpolates: the clips run near 30 fps and
    // the screen at 60, so stepping frame to frame would make the correction itself judder.
    private float sample(float phase) {
        float position = phase * (profile.length - 1);
        int index = (int) position;
        if (index >= profile.length - 1) {
            return profile[profile.length - 1];
        }
        float fraction = position - index;
        return profile[index] + (profile[index + 1] - profile[index]) * fraction;
    }
}
