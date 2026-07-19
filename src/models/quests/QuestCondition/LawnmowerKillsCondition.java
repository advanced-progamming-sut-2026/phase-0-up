package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when at least `threshold` zombies were killed by lawn mowers (Mowing Time).
public class LawnmowerKillsCondition implements QuestCondition {
    private final int threshold;

    public LawnmowerKillsCondition(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.getLawnmowerKills() >= threshold;
    }
}
