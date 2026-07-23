package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied once the player has won `threshold` levels in a row at maximum difficulty (Win After Win).
// The streak is persisted on the profile across levels and folded into the context at level end, so
// this only reads the running total; a loss (or a win below max difficulty) resets it to zero.
public class WinStreakCondition implements QuestCondition {
    private final int threshold;

    public WinStreakCondition(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.getWinStreakAtMaxDifficulty() >= threshold;
    }

    // Cross-level: the streak is kept on the profile between matches (and reset there by a loss), so
    // the travel log shows how many wins of the run are already banked.
    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        int streak = profile == null ? 0 : profile.getWinStreakAtMaxDifficulty();
        return models.quests.QuestProgress.crossLevel(streak, threshold);
    }
}
