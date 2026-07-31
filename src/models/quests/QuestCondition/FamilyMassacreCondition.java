package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the level is won and every zombie kill was credited to ONE NAMED plant family (Family
// Massacre: "use only plants of family X to kill zombies"). The family is authored on the quest, so
// sweeping the lawn with a different family does not count -- it has to be that one.
//
// Environmental kills (mower, nuke) credit no family and so never break the "only" clause; any kill by
// a second family does.
public class FamilyMassacreCondition implements QuestCondition {
    private final String family;

    public FamilyMassacreCondition(String family) {
        this.family = family;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        // The named family got at least one kill, and it is the ONLY family credited with any.
        return ctx.isWon() && ctx.killsByFamily(family) > 0 && ctx.distinctKillerFamilies() == 1;
    }
}
