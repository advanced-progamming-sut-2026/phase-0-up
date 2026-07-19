package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the level is won having lost no more than `maxLost` plants (Economical Herbivore).
public class MaxPlantsLostCondition implements QuestCondition {
    private final int maxLost;

    public MaxPlantsLostCondition(int maxLost) {
        this.maxLost = maxLost;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.isWon() && ctx.getPlantsLost() <= maxLost;
    }
}
