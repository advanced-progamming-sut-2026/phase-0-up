package views.gdx.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Guards the camera shake, whose whole effect is motion and is therefore invisible to a screenshot --
// and worse, a still frame cannot tell a correct shake from a camera that has come off its rails.
//
// Three properties are what make it safe to apply to a live camera, and all three are arithmetic:
// it must return to exactly zero (or the board rests off-centre for the remainder of the level), it
// must never move further than the margin the zoom-in creates (or the view shows clear colour past the
// edge of the painted background, which reads as a rendering failure), and it must be raised by the
// sentences the model actually emits (a regex that drifts from the model fails silently -- nothing
// shakes, and nothing says why).
class CameraShakeTest {

    // The two view sizes GameScreen runs at: FitViewport(1365, 768).
    private static final float VIEW_W = 1365f;
    private static final float VIEW_H = 768f;

    private static final float FRAME = 1f / 60f;

    // The exact sentences the model emits. AreaExplosiveAbility.detonate and KillPlantsAbility's
    // no-torch branch; copied from their format strings rather than paraphrased.
    private static final String DETONATION = "Cherry Bomb detonates at (3, 2)!";
    private static final String SMASH = "ZombieGargantuar smashes Peashooter to pieces at (3, 2).";

    @Test
    @DisplayName("a detonation and a Gargantuar's smash both raise a shake")
    void bothEventsRaiseIt() {
        CameraShake explosion = new CameraShake();
        explosion.onEvent(DETONATION);
        assertTrue(explosion.isShaking(), DETONATION);

        CameraShake smash = new CameraShake();
        smash.onEvent(SMASH);
        assertTrue(smash.isShaking(), SMASH);

        // The hammer hits harder than the bomb, deliberately -- see CameraShake.
        assertTrue(smash.intensity() > explosion.intensity());
    }

    @Test
    @DisplayName("nothing else in the event stream shakes the camera")
    void ignoresEverythingElse() {
        CameraShake shake = new CameraShake();
        // The torch branch of the very same ability, the hypnotised-crush line that shares its verb,
        // and a sample of the ordinary narration the same stream carries.
        for (String quiet : new String[] {
                "ZombieExplorer sets Peashooter ablaze at (3, 2).",
                "ZombieGargantuar smashes your hypnotized ZombieBrowncoat.",
                "Wave 1 started.",
                "The tornado drops ZombieGargantuar into lane 2, 3 column(s) past the edge.",
                "ZombieGargantuar hurls its Imp over your defences onto (2, 3)!",
                null}) {
            shake.onEvent(quiet);
            assertFalse(shake.isShaking(), "should not shake on: " + quiet);
        }
    }

    @Test
    @DisplayName("it decays to exactly zero, and the camera lands back on its resting place")
    void decaysToZero() {
        CameraShake shake = new CameraShake();
        shake.onEvent(SMASH);

        // Two seconds is comfortably past the longest a full-strength shake lasts.
        for (int frame = 0; frame < 120; frame++) {
            shake.advance(FRAME);
        }
        assertFalse(shake.isShaking());
        // Not "close to zero": a residual offset is a board that never comes back to centre.
        assertEquals(0f, shake.intensity(), 0f);
        assertEquals(0f, shake.offsetX(VIEW_W), 0f);
        assertEquals(0f, shake.offsetY(VIEW_H), 0f);
        assertEquals(1f, shake.zoom(), 0f);
    }

    @Test
    @DisplayName("the offset never exceeds the margin the zoom-in makes, on either axis")
    void staysInsideTheMarginItMakes() {
        CameraShake shake = new CameraShake();
        shake.onEvent(SMASH);

        for (int frame = 0; frame < 120; frame++) {
            // Re-raised part way through, which is both the worst case for the bound and the ordinary
            // case on a board where two things blow up at once.
            if (frame == 20) {
                shake.onEvent(DETONATION);
            }
            shake.advance(FRAME);

            // Zooming in by (1 - zoom) frees half of that on each side. This is the whole reason the
            // shake is legal: the camera frames the background's full 768px height exactly, so any
            // movement outside this margin shows clear colour along an edge.
            float slack = 1f - shake.zoom();
            float marginX = VIEW_W * slack * 0.5f;
            float marginY = VIEW_H * slack * 0.5f;

            assertTrue(Math.abs(shake.offsetX(VIEW_W)) <= marginX + 1e-4f,
                    "frame " + frame + ": x " + shake.offsetX(VIEW_W) + " past margin " + marginX);
            assertTrue(Math.abs(shake.offsetY(VIEW_H)) <= marginY + 1e-4f,
                    "frame " + frame + ": y " + shake.offsetY(VIEW_H) + " past margin " + marginY);
        }
    }

    @Test
    @DisplayName("trauma saturates, so a chain of blasts cannot shake the board apart")
    void traumaSaturates() {
        CameraShake shake = new CameraShake();
        for (int i = 0; i < 20; i++) {
            shake.onEvent(DETONATION);
        }
        assertEquals(1f, shake.intensity(), 1e-5f);

        CameraShake once = new CameraShake();
        once.add(1f);
        // Twenty blasts and one maximal hit are the same shake, which is what the cap is for.
        assertEquals(once.intensity(), shake.intensity(), 1e-5f);
    }

    @Test
    @DisplayName("a small aftershock cannot cut a big shake short")
    void aftershockAddsRatherThanReplaces() {
        CameraShake shake = new CameraShake();
        shake.add(0.9f);
        float before = shake.intensity();
        shake.onEvent(DETONATION);
        assertTrue(shake.intensity() >= before, "adding trauma must never reduce it");
    }

    @Test
    @DisplayName("the two axes do not move together, so the shake is not one diagonal line")
    void axesAreOutOfStep() {
        CameraShake shake = new CameraShake();
        shake.add(1f);
        // Sampled across a whole shake's worth of frames: if x and y were in phase their ratio would be
        // constant, so any two frames disagreeing about it is enough to prove they are not.
        float first = ratio(shake);
        boolean differs = false;
        for (int frame = 0; frame < 30 && !differs; frame++) {
            shake.advance(FRAME);
            shake.add(1f);   // held at full strength, so only the waveform is varying
            differs = Math.abs(ratio(shake) - first) > 0.1f;
        }
        assertTrue(differs, "x and y move in lockstep, which reads as a swing rather than a shock");
    }

    private static float ratio(CameraShake shake) {
        float x = shake.offsetX(VIEW_W);
        return x == 0f ? Float.MAX_VALUE : shake.offsetY(VIEW_H) / x;
    }
}
