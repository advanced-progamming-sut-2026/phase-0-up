package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the player banked at least `threshold` sun over the level.
public class CollectSunCondition implements QuestCondition {
    private final int threshold;

    public CollectSunCondition(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.getSunCollected() >= threshold;
    }
}
