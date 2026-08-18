package views.gdx.sprite;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Guards foot planting, whose whole effect is motion and therefore invisible to a screenshot.
//
// Three properties make the offset safe to apply to a live renderer, and all three are arithmetic:
// it must be zero at both ends of the cycle (or a looping clip jumps at the seam), it must never
// accumulate (or the drawing drifts away from the model it is supposed to be decorating), and it must
// lag through the slow part of the step (which is what plants the foot). If any of them breaks, the
// zombies either judder, slide off their own hitboxes, or simply stop looking any different.
class WalkCycleTest {

    private static final float DURATION = 3f;

    // Only what WalkCycle asks for; anything else throws, so a change that starts consulting the rest of
    // the sprite fails here rather than quietly in the renderer.
    private static final class FakeSprite implements EntitySprite {
        private final Rectangle[] swatch;

        FakeSprite(float... xs) {
            if (xs == null) {
                this.swatch = null;
                return;
            }
            this.swatch = new Rectangle[xs.length];
            for (int i = 0; i < xs.length; i++) {
                this.swatch[i] = new Rectangle(xs[i], 0f, 4f, 4f);
            }
        }

        @Override
        public Rectangle[] partBoundsByFrame(String clip, String partName) {
            return WalkCycle.GROUND_SWATCH.equals(partName) ? swatch : null;
        }

        @Override
        public float clipDuration(String clip) {
            return DURATION;
        }

        @Override
        public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, String clip, float stateTime,
                         float x, float y, boolean faceRight) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, String clip, float stateTime,
                         float x, float y, boolean faceRight, java.util.Map<String, Boolean> parts) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public boolean isAnimated() {
            return true;
        }

        @Override
        public boolean hasClip(String clip) {
            return true;
        }

        @Override
        public Set<String> clips() {
            return Set.of("walk");
        }

        @Override
        public boolean hasPart(String partName) {
            return WalkCycle.GROUND_SWATCH.equals(partName);
        }

        @Override
        public Rectangle bounds(String clip) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Rectangle visibleBounds(String clip, Set<String> hidden) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Rectangle partBounds(String clip, String partName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Rectangle anchorBounds() {
            throw new UnsupportedOperationException();
        }
    }

    // A stance-then-swing path, the shape ZombieDefault really has: barely moving for the first half,
    // then covering most of the stride quickly.
    private static WalkCycle stepped() {
        return WalkCycle.of(new FakeSprite(0f, 5f, 10f, 15f, 20f, 60f, 80f, 90f, 100f), "walk");
    }

    @Test
    @DisplayName("the lead is zero at both ends, so a looping clip never jumps at the seam")
    void zeroAtBothEnds() {
        WalkCycle walk = stepped();
        assertNotNull(walk);
        assertEquals(0f, walk.lead(0f), 1e-4f);
        assertEquals(0f, walk.lead(DURATION), 1e-4f);
    }

    @Test
    @DisplayName("the lead never accumulates: every cycle returns to where it started")
    void neverAccumulates() {
        WalkCycle walk = stepped();
        // The renderer wraps stateTime into the clip, so this is what it actually sees cycle after cycle.
        for (int cycle = 0; cycle < 5; cycle++) {
            assertEquals(0f, walk.lead(0f), 1e-4f, "start of cycle " + cycle);
            assertEquals(0f, walk.lead(DURATION), 1e-4f, "end of cycle " + cycle);
        }
    }

    @Test
    @DisplayName("the body lags through the stance, which is what plants the foot")
    void lagsThroughTheStance() {
        WalkCycle walk = stepped();
        // Halfway through the clip only a fifth of the stride has been covered, so the straight line is
        // well ahead of the body.
        float mid = walk.lead(DURATION * 0.5f);
        assertTrue(mid < -0.1f, "expected a clear lag mid-stance, got " + mid);

        // Bounded, in both directions. Deliberately NOT "never runs ahead": a swing that covers more than
        // its share of the stride leaves the body briefly in front of the straight line before the next
        // stance pulls it back, which is real and correct. ZombieDefault's own curve happens never to do
        // it, but that is a fact about that gait, not about this arithmetic -- asserting it here failed on
        // the very first synthetic path with a sharp swing in it.
        for (int i = 0; i <= 20; i++) {
            float lead = walk.lead(DURATION * i / 20f);
            assertTrue(Math.abs(lead) <= 1f,
                    "phase " + (i / 20f) + " moved more than a whole stride: " + lead);
        }
    }

    @Test
    @DisplayName("a perfectly even path needs no correction at all")
    void evenPathCorrectsNothing() {
        WalkCycle walk = WalkCycle.of(new FakeSprite(0f, 25f, 50f, 75f, 100f), "walk");
        assertNotNull(walk);
        for (int i = 0; i <= 8; i++) {
            assertEquals(0f, walk.lead(DURATION * i / 8f), 1e-4f, "phase " + (i / 8f));
        }
    }

    @Test
    @DisplayName("the correction is interpolated between frames rather than stepped")
    void interpolatesBetweenFrames() {
        WalkCycle walk = WalkCycle.of(new FakeSprite(0f, 0f, 100f), "walk");
        assertNotNull(walk);
        // A quarter of the way in is halfway between frame 0 and frame 1, both of which sit at 0 travel,
        // so the body has covered nothing while the line has covered a quarter.
        assertEquals(-0.25f, walk.lead(DURATION * 0.25f), 1e-3f);
        // Three quarters is halfway between frames 1 and 2: half the stride covered against three
        // quarters of the time. A stepped sampler would still be reporting frame 1's value here.
        assertEquals(-0.25f, walk.lead(DURATION * 0.75f), 1e-3f);
    }

    @Test
    @DisplayName("clips that cannot drive a walk are refused rather than guessed at")
    void refusesWhatItCannotUse() {
        assertNull(WalkCycle.of(new FakeSprite((float[]) null), "walk"),
                "a clip that never poses the swatch -- which is how idle, eat and die opt out");
        assertNull(WalkCycle.of(new FakeSprite(0f, 1f), "walk"), "too few frames to be a cycle");
        assertNull(WalkCycle.of(new FakeSprite(0f, 1f, 0.5f, 1f), "walk"),
                "a swatch that barely travels would amplify noise into a stutter");
        assertNull(WalkCycle.of(null, "walk"));
        assertNull(WalkCycle.of(new FakeSprite(0f, 50f, 100f), null));
    }
}
