package models.quests.QuestCondition;

import models.quests.QuestContext;

// Won with every kill credited to ONE NAMED family (Family Massacre); mower kills credit none.
public class FamilyMassacreCondition implements QuestCondition {
    private final String family;
    public FamilyMassacreCondition(String family) {
        this.family = family;
    }
    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.isWon() && ctx.killsByFamily(family) > 0 && ctx.distinctKillerFamilies() == 1;
    }
}
