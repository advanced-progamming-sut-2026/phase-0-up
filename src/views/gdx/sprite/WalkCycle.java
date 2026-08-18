package views.gdx.sprite;

import com.badlogic.gdx.math.Rectangle;

// Foot planting: the drawing offset that stops a walking zombie's feet skating along the ground.
//
// The model moves a zombie at a constant speed, and that must stay true -- it is what a server can
// compute, and there is no PAM anywhere near it. But walking is not a constant glide: a step plants a
// foot, holds it while the body passes over, then swings it forward. Drawn at a linearly increasing x,
// the planted foot slides backwards under the zombie for the whole stance. That is the skate.
//
// Every zombie animation carries a `ground_swatch` part for exactly this. It marks where the ground was
// when the step began, and travels backwards through the clip as the body advances over it. Its path is
// therefore the body's forward travel, in the animation's own rhythm -- slow during a stance, quick
// during a swing. ZombieDefault's runs 0 -> 100 units over 90 frames, but unevenly: barely 20 units
// across the first 40 frames, then 38 across the next 16.
//
// **What this class hands back is a SHAPE, not a distance.** p(t) is that path normalised to [0, 1], so
// it carries the rhythm and nothing else -- no scale, no mirroring, no dependence on how big the
// animation was authored. The caller multiplies it by how far the MODEL travels in one cycle:
//
//     offset(t) = stride * ( p(t) - t/T )
//
// which is the animation's profile minus the straight line through the same two endpoints. Three
// properties fall out of that, and all three matter:
//
//   * it is zero at t=0 and t=T, so a looping clip never jumps at the seam;
//   * it never accumulates, so the drawing can never drift away from the model;
//   * the amplitude follows the model's real speed, so a chilled zombie at half pace gets half the
//     wobble rather than the wobble its animation was authored for.
//
// The zombie still arrives exactly where and when the model says. Only the way it gets there is
// borrowed from the artwork. Nothing here reaches the model, which is what keeps it safe to run on a
// client whose server knows nothing about PAMs.
public final class WalkCycle {

    public static final String GROUND_SWATCH = "ground_swatch";

    // Below this the swatch is not really travelling and the normalisation would amplify noise into a
    // stutter. In animation units -- ZombieDefault's real travel is 100.
    private static final float MIN_TRAVEL = 4f;

    // p(t) sampled per frame: 0 at the first, 1 at the last.
    private final float[] profile;
    private final float duration;

    private WalkCycle(float[] profile, float duration) {
        this.profile = profile;
        this.duration = duration;
    }

    // Null when this clip cannot drive a walk: no swatch posed in it (which is how idle, eat and die
    // exclude themselves -- they simply do not carry the part), too few frames, or a swatch that barely
    // moves. Callers fall back to drawing at the model's position, exactly as before.
    public static WalkCycle of(EntitySprite sprite, String clip) {
        if (sprite == null || clip == null) {
            return null;
        }
        float duration = sprite.clipDuration(clip);
        if (duration <= 0f) {
            return null;
        }
        Rectangle[] frames = sprite.partBoundsByFrame(clip, GROUND_SWATCH);
        if (frames == null || frames.length < 3) {
            return null;
        }

        float first = Float.NaN;
        float last = Float.NaN;
        float[] path = new float[frames.length];
        for (int i = 0; i < frames.length; i++) {
            if (frames[i] == null) {
                path[i] = Float.NaN;
                continue;
            }
            path[i] = frames[i].x;
            if (Float.isNaN(first)) {
                first = path[i];
            }
            last = path[i];
        }
        if (Float.isNaN(first) || Math.abs(last - first) < MIN_TRAVEL) {
            return null;
        }

        float travel = last - first;
        float[] profile = new float[frames.length];
        float previous = 0f;
        for (int i = 0; i < path.length; i++) {
            // A frame the clip does not pose holds the previous value rather than tearing the curve.
            profile[i] = Float.isNaN(path[i]) ? previous : (path[i] - first) / travel;
            previous = profile[i];
        }
        return new WalkCycle(profile, duration);
    }

    // How far ahead of its straight-line position the zombie should be drawn, as a fraction of one
    // cycle's travel. Negative through most of a stance -- the body is waiting on a planted foot while
    // the straight line runs on ahead -- and back to zero by the end of the cycle.
    public float lead(float stateTime) {
        if (duration <= 0f) {
            return 0f;
        }
        float phase = Math.min(Math.max(stateTime / duration, 0f), 1f);
        return sample(phase) - phase;
    }

    // Linear between frames. Stepping straight from one frame to the next is visible: the clips run at
    // about 30 fps and the screen at 60, so every second frame would repeat the same offset and the
    // correction itself would judder.
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
