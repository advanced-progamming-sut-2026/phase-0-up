package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the level is won with a given column left empty of plants (One Column Less).
public class EmptyColumnCondition implements QuestCondition {
    private final int column;

    public EmptyColumnCondition(int column) {
        this.column = column;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.isWon() && ctx.isColumnEmpty(column);
    }
}
