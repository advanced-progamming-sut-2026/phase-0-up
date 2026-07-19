package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the level is won with exactly zero sun left in the bank (Defense Master).
public class ZeroSunCondition implements QuestCondition {
    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.isWon() && ctx.getFinalSun() == 0;
    }
}
