package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the level is won with a given row left empty of plants (Defenseless Row).
public class EmptyRowCondition implements QuestCondition {
    private final int row;

    public EmptyRowCondition(int row) {
        this.row = row;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.isWon() && ctx.isRowEmpty(row);
    }
}
