package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the player banked at least `threshold` sun over the level.
public class CollectSunCondition implements QuestCondition {
    private final int threshold;

    public CollectSunCondition(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.getSunCollected() >= threshold;
    }

    // A single-level goal: nothing carries between matches, so the travel log shows the
    // target with no running tally rather than implying progress that would not persist.
    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        return models.quests.QuestProgress.perLevel(threshold);
    }
}
