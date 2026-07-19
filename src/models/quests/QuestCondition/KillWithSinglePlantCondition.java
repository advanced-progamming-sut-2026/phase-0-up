package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when at least `threshold` zombies were killed and every credited kill went to the same
// single plant type -- whichever one the player chose (Pro Plant Player).
public class KillWithSinglePlantCondition implements QuestCondition {
    private final int threshold;

    public KillWithSinglePlantCondition(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        if (ctx.distinctKillerPlants() != 1) {
            return false;
        }
        // Exactly one plant is credited; its count is the sole value in the map.
        return ctx.getKillsByPlant().values().iterator().next() >= threshold;
    }
}
