package models.quests.QuestCondition;

import models.quests.QuestContext;

// Won with at most `count` plants of ONE category (Cloudy Day) -- a cap on that category only.
public class OnlyCategoryCondition implements QuestCondition {
    private final String category;
    private final int count;

    public OnlyCategoryCondition(String category, int count) {
        this.category = category;
        this.count = count;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        // At most `count` of the capped category, and at least one: it is a budget, not an absence.
        int placed = ctx.plantedCategoryCount(category);
        return ctx.isWon() && placed > 0 && placed <= count;
    }

    // A single-level goal: nothing carries between matches, so the travel log shows the
    // target with no running tally rather than implying progress that would not persist.
    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        return models.quests.QuestProgress.perLevel(count);
    }
}
