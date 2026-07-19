package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the level is won with both column n and row n left empty of plants (Defenseless Cross).
public class EmptyCrossCondition implements QuestCondition {
    private final int index;

    public EmptyCrossCondition(int index) {
        this.index = index;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.isWon() && ctx.isCrossEmpty(index);
    }
}
