package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the level is won using only plants of one category, with exactly `count` of them
// placed -- e.g. only 3 sun-producing plants (Cloudy Day).
public class OnlyCategoryCondition implements QuestCondition {
    private final String category;
    private final int count;

    public OnlyCategoryCondition(String category, int count) {
        this.category = category;
        this.count = count;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        // Exactly `count` plants of this category, and every plant placed was that category.
        return ctx.isWon() && ctx.plantedCategoryCount(category) == count && ctx.allPlantedAreCategory(category);
    }

    // A single-level goal: nothing carries between matches, so the travel log shows the
    // target with no running tally rather than implying progress that would not persist.
    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        return models.quests.QuestProgress.perLevel(count);
    }
}
