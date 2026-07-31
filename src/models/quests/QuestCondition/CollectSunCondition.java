package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when `threshold` sun is banked over one calendar day (Daily Sun Catcher), not one level.
public class CollectSunCondition implements QuestCondition {
    private final int threshold;

    public CollectSunCondition(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.getSunCollectedToday() >= threshold;
    }

    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        int collected = profile == null ? 0 : profile.getSunCollectedToday();
        return models.quests.QuestProgress.crossLevel(collected, threshold);
    }
}
