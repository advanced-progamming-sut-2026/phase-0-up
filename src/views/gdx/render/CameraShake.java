package views.gdx.render;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The kick the camera takes when something hits the lawn hard.
//
// Driven off the model's own narration, the same seam ExplosionEffects, WeatherEffects and
// ZombieActions already read: a detonation and a Gargantuar's hammer are both instants, over before the
// next tick, and neither leaves anything on the board to infer them from afterwards. So each announces
// itself in a sentence and this listens for its two.
//
// No LibGDX type appears here on purpose. The whole class is arithmetic -- how hard, which way, for how
// long -- and keeping it that way is what lets the curve be pinned by a unit test rather than judged
// from a screenshot, which for a motion effect is no judgement at all.
public final class CameraShake {

    // "Cherry Bomb detonates at (3, 2)!" -- AreaExplosiveAbility.detonate. Same sentence
    // ExplosionEffects draws the blast from; this one only needs to know that it happened.
    private static final Pattern DETONATION =
            Pattern.compile("^(.+?) detonates at \\((\\d+), (\\d+)\\)!$");

    // "ZombieGargantuar smashes Peashooter to pieces at (3, 2)." -- KillPlantsAbility, on the branch
    // that does NOT require a torch. The torch branch says "sets ... ablaze at (" and is deliberately
    // not matched: a plant catching fire is not an impact.
    private static final Pattern SMASH =
            Pattern.compile("^(.+?) smashes (.+?) to pieces at \\((\\d+), (\\d+)\\)\\.$");

    // How much trauma each event is worth, on a 0..1 scale.
    //
    // The smash is the heavier of the two, which is not the obvious ordering -- a Cherry Bomb is the
    // bigger bang. But a blast is something the PLAYER set off and is already watching, while a
    // Gargantuar's hammer is a two-ton mallet landing on ground the player is standing on, and it is
    // rare enough to be worth flinching at.
    // Both are high on the scale because intensity is the SQUARE of trauma (see below): 0.75 of the way
    // up the dial is a little over half strength once squared, which is where a blast wants to be. The
    // first pass at these was 0.55/0.80, and measured on a real Jalapeno that came out as a 3px nudge --
    // technically a shake, invisible in practice.
    private static final float TRAUMA_EXPLOSION = 0.75f;
    private static final float TRAUMA_SMASH = 1.00f;

    // Trauma bleeds off linearly, so a full-strength shake is over in about 0.6s and a blast in 0.47s.
    private static final float DECAY_PER_SECOND = 1.6f;

    // Peak displacement in world units, which are background pixels (see BackgroundRenderer). The view
    // is 1365 of them wide and is drawn across the whole window, so on a 1920px screen sixteen of them
    // is about 22 real pixels at the top of a smash -- felt clearly, and over before it is in the way.
    private static final float MAX_OFFSET_X = 16f;
    private static final float MAX_OFFSET_Y = 10f;

    // How far the camera pushes in while shaking. This is not decoration: it is what MAKES the shake
    // legal.
    //
    // The camera frames the background's full 768px height exactly -- FitViewport's world height IS
    // WORLD_HEIGHT, and BackgroundRenderer draws the art at native size -- so there is not one spare
    // pixel above or below the art. A camera nudged up by so much as a pixel shows clear colour along
    // the top edge, which reads as the renderer failing rather than as a shake. Zooming in first
    // manufactures the margin, and the offsets below are then clamped to exactly the margin it made, so
    // the edge cannot be reached however the constants are later tuned.
    // Sized so the margin it frees is never the thing limiting the shake: 3% of 768 is 11.5 units of
    // vertical headroom against a 10-unit peak, and 20 of horizontal against 16. It is also felt in its
    // own right -- a hair of push-in on the frame something detonates is most of what sells the hit.
    private static final float ZOOM_PUNCH = 0.03f;

    // Two sine waves per axis, at frequencies with no common factor worth speaking of, summed with
    // weights that add to one.
    //
    // A single sine is a pendulum, and the eye reads a pendulum as a swing rather than as a shock. Two
    // out of step never repeat within the half-second a shake lasts, so the motion is jittery without
    // any randomness -- which matters, because a random offset cannot be tested and would differ every
    // time the same explosion was screenshotted.
    private static final float[] FREQ_X = {19f, 31f};
    private static final float[] FREQ_Y = {23f, 37f};
    private static final float[] PHASE_X = {0f, 0f};
    // The vertical wave starts elsewhere in its cycle, or both axes peak together and the whole shake
    // runs along one diagonal.
    private static final float[] PHASE_Y = {1.7f, 0.4f};
    private static final float[] WEIGHTS = {0.6f, 0.4f};

    private float trauma;
    private float clock;

    // Offered every event the model drains, alongside the explosions, the weather and the zombie
    // actions. Anything that is neither a detonation nor a smash is ignored, so this needs to know
    // nothing about the other consumers of the same stream.
    public void onEvent(String message) {
        if (message == null) {
            return;
        }
        String text = message.trim();
        if (DETONATION.matcher(text).matches()) {
            add(TRAUMA_EXPLOSION);
            log(text, TRAUMA_EXPLOSION);
            return;
        }
        Matcher smash = SMASH.matcher(text);
        if (smash.matches()) {
            add(TRAUMA_SMASH);
            log(text, TRAUMA_SMASH);
        }
    }

    // Trauma ADDS and saturates rather than being replaced. A second blast during the first one should
    // extend and deepen the shake; assigning would let a small aftershock cut a big one short.
    public void add(float amount) {
        trauma = Math.min(1f, Math.max(0f, trauma + amount));
    }

    // Once per frame, before the camera is positioned.
    //
    // Called with the frame's real delta rather than the animation delta the board is drawn with: this
    // belongs to the presentation rather than to the simulation, and a pause landing mid-shake would
    // otherwise freeze the lawn permanently off-centre with no way back.
    public void advance(float delta) {
        clock += delta;
        if (trauma > 0f) {
            trauma = Math.max(0f, trauma - DECAY_PER_SECOND * delta);
        }
    }

    public boolean isShaking() {
        return trauma > 0f;
    }

    // Squared, so the tail of a shake is genuinely small. Linear decay applied straight to the offset
    // spends most of its life at a medium wobble, which reads as the camera being loose rather than as
    // something having hit it.
    public float intensity() {
        return trauma * trauma;
    }

    public float zoom() {
        return 1f - ZOOM_PUNCH * intensity();
    }

    public float offsetX(float viewWidth) {
        return amplitude(MAX_OFFSET_X, viewWidth) * wave(FREQ_X, PHASE_X);
    }

    public float offsetY(float viewHeight) {
        return amplitude(MAX_OFFSET_Y, viewHeight) * wave(FREQ_Y, PHASE_Y);
    }

    // The most the camera may move on this axis: whichever is smaller of the displacement we want and
    // the margin the zoom just created, scaled by how hard the hit was.
    //
    // Zooming in by ZOOM_PUNCH * intensity frees half of that on each side, so the second term is
    // exactly the margin -- meaning the offset can never reach the edge of the art no matter what the
    // constants above are set to. That is the property worth having; the numbers themselves are taste.
    private float amplitude(float max, float viewSize) {
        return Math.min(max, viewSize * ZOOM_PUNCH * 0.5f) * intensity();
    }

    // In [-1, 1] by construction, because the weights sum to one.
    private float wave(float[] frequencies, float[] phases) {
        float sum = 0f;
        for (int i = 0; i < frequencies.length; i++) {
            sum += WEIGHTS[i] * (float) Math.sin(clock * frequencies[i] + phases[i]);
        }
        return sum;
    }

    private void log(String message, float amount) {
        if (views.gdx.core.DebugFlags.SHAKE_CHECK) {
            com.badlogic.gdx.Gdx.app.log("CameraShake",
                    "raised " + amount + " -> trauma " + trauma + " on: " + message);
        }
    }
}
