package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the level is won having placed at least `threshold` plants of a given category, e.g.
// three EXPLOSIVE plants (Pro Demolisher).
public class PlantCategoryCountCondition implements QuestCondition {
    private final String category;
    private final int threshold;

    public PlantCategoryCountCondition(String category, int threshold) {
        this.category = category;
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.isWon() && ctx.plantedCategoryCount(category) >= threshold;
    }
}
