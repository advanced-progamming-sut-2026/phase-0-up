package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the level is won and every zombie kill was credited to plants of a single family
// (category) -- whichever family the player leaned on (Family Massacre). Environmental kills (mower,
// nuke) credit no family and so never break the "one family" clause; any kill by a second family does.
public class FamilyMassacreCondition implements QuestCondition {
    @Override
    public boolean isSatisfied(QuestContext ctx) {
        // Exactly one family shows up in the credited-kill tally (and it only shows up because it got
        // at least one kill), so this is both "only one family" and "at least one kill".
        return ctx.isWon() && ctx.distinctKillerFamilies() == 1;
    }
}
