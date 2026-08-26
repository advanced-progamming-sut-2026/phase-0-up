package views.gdx.ui;

// How far through a level's waves the horde is, as one number between 0 and 1.
//
// ## Why this is not just current/total
//
// It was, and that made the meter a staircase. The bar jumped a whole wave's width the instant a wave
// LAUNCHED and then sat perfectly still for as long as that wave took to clear -- so the stretch of the
// level a progress bar is most wanted for, grinding a wave down, is precisely the stretch it reported
// nothing about. And because it counted launches, it read 100% while the final wave was still walking
// on, with every zombie in it alive.
//
// The fix needs no new model state. A Wave already knows what fraction of its starting HP is gone, and
// that number is not incidental -- it is the very thing WaveSystem watches to decide when to release the
// next wave. So the bar now advances on exactly what brings the next wave closer.
//
// ## The hand-over
//
// Wave N launching moves the first term from N-1 to N while the second drops back to 0, so the total
// never goes backwards. It does step forward once per wave, by whatever share of the previous wave was
// still standing when the next one was released -- the threshold is 75%, so a quarter of one wave's
// width, and WaveBar's easing absorbs it.
//
// Kept apart from GameHud because it is arithmetic, and arithmetic that was wrong once: separated, it
// can be checked without a Stage, a Skin or a GL context.
public final class WaveProgress {

    private WaveProgress() {
    }

    /**
     * @param wavesStarted    how many waves have launched (GameSession.getCurrentWave)
     * @param totalWaves      how many the level holds
     * @param clearedOfCurrent fraction of the current wave's HP already gone, 0..1
     */
    public static float of(int wavesStarted, int totalWaves, float clearedOfCurrent) {
        if (totalWaves <= 0 || wavesStarted <= 0) {
            return 0f;
        }
        // Waves fully behind us. Clamped to the roster, because a level whose last wave has launched
        // still reports that wave as "started" and must not count itself twice.
        float behind = Math.min(wavesStarted, totalWaves) - 1;
        float within = Math.max(0f, Math.min(1f, clearedOfCurrent));
        return Math.max(0f, Math.min(1f, (behind + within) / totalWaves));
    }
}
