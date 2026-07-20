package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the player killed at least `threshold` zombies with a named plant and credited no
// kills to any other plant (Only Cactus). Environmental kills (mower, nuke) don't count against it --
// they credit no plant -- but any kill by a different plant breaks the "only" clause.
public class KillWithPlantCondition implements QuestCondition {
    private final String plantName;
    private final int threshold;

    public KillWithPlantCondition(String plantName, int threshold) {
        this.plantName = plantName;
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.killsByPlant(plantName) >= threshold && ctx.distinctKillerPlants() <= 1;
    }
}
