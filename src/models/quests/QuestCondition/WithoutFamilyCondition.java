package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the level is won without ever placing a plant of a given family/category, and at
// least one plant was placed (Flourish in Constraints). The excluded family is authored on the quest.
public class WithoutFamilyCondition implements QuestCondition {
    private final String family;

    public WithoutFamilyCondition(String family) {
        this.family = family;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.isWon() && ctx.plantedCount() > 0 && ctx.plantedCategoryCount(family) == 0;
    }
}
