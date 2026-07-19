package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the level is won with a mirror-symmetric garden (Symmetry). A `symmetric` flag of
// false inverts the test for the asymmetry quest (OCD).
public class SymmetryCondition implements QuestCondition {
    private final boolean symmetric;

    public SymmetryCondition(boolean symmetric) {
        this.symmetric = symmetric;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.isWon() && ctx.isVerticallySymmetric() == symmetric;
    }
}
