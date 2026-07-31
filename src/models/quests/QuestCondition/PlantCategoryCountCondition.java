package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when `threshold` plants of a category are placed in a SINGLE level (Pro Demolisher).
public class PlantCategoryCountCondition implements QuestCondition {
    private final String category;
    private final int threshold;

    public PlantCategoryCountCondition(String category, int threshold) {
        this.category = category;
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.plantedCategoryCount(category) >= threshold;
    }

    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        return models.quests.QuestProgress.perLevel(threshold);
    }
}
