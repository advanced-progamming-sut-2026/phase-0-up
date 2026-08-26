package views.gdx.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The wave meter's arithmetic, which was a staircase.
//
// current/total jumped a whole wave's width the moment a wave LAUNCHED, then held still for as long as
// that wave took to clear -- and reported 100% while the final wave was still walking on with every
// zombie alive. Neither is visible in a screenshot of any single frame: a bar sitting at 25% looks
// exactly as correct as a bar that should be at 12%.
//
// So the numbers are pinned. Four waves throughout, because a quarter is easy to read by eye.
class WaveProgressTest {

    private static final float EPSILON = 0.0001f;

    @Test
    void nothingHasStartedSoNothingIsShown() {
        assertEquals(0f, WaveProgress.of(0, 4, 0f), EPSILON);
        // A level with no waves at all -- a mini-game -- must not divide by it.
        assertEquals(0f, WaveProgress.of(1, 0, 0.5f), EPSILON);
        assertEquals(0f, WaveProgress.of(-1, 4, 0.5f), EPSILON);
    }

    // The old bug, stated directly: launching wave 1 used to put the bar at a quarter.
    @Test
    void launchingAWaveDoesNotAdvanceTheBarByItself() {
        assertEquals(0f, WaveProgress.of(1, 4, 0f), EPSILON);
        assertEquals(0.25f, WaveProgress.of(2, 4, 0f), EPSILON);
    }

    @Test
    void theBarCreepsAsTheCurrentWaveIsGroundDown() {
        assertEquals(0.125f, WaveProgress.of(1, 4, 0.5f), EPSILON);
        assertEquals(0.1875f, WaveProgress.of(1, 4, 0.75f), EPSILON);
        // Second wave: a full quarter behind us, plus half of the next quarter.
        assertEquals(0.375f, WaveProgress.of(2, 4, 0.5f), EPSILON);
    }

    // WaveSystem releases the next wave at 75% cleared, so this is the one discontinuity the meter has.
    // It must go FORWARD -- a bar that slips back reads as a bug even when the number is defensible.
    @Test
    void theHandOverBetweenWavesNeverGoesBackwards() {
        float justBeforeRelease = WaveProgress.of(1, 4, 0.75f);
        float justAfterRelease = WaveProgress.of(2, 4, 0f);

        assertTrue(justAfterRelease >= justBeforeRelease,
                justAfterRelease + " should not be behind " + justBeforeRelease);
        // And the step is only the quarter of a wave that was still standing.
        assertEquals(0.0625f, justAfterRelease - justBeforeRelease, EPSILON);
    }

    // The other end of the old bug: the bar used to read full while the last wave was still arriving.
    @Test
    void theFinalWaveHasToBeClearedBeforeTheBarIsFull() {
        assertEquals(0.75f, WaveProgress.of(4, 4, 0f), EPSILON);
        assertEquals(0.875f, WaveProgress.of(4, 4, 0.5f), EPSILON);
        assertEquals(1f, WaveProgress.of(4, 4, 1f), EPSILON);
    }

    @Test
    void nothingCanPushItPastEitherEnd() {
        assertEquals(1f, WaveProgress.of(9, 4, 1f), EPSILON);
        assertEquals(1f, WaveProgress.of(4, 4, 5f), EPSILON);
        assertEquals(0.75f, WaveProgress.of(4, 4, -3f), EPSILON);
    }
}
