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
}
