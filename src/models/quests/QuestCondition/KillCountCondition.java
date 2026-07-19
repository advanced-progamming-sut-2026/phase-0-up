package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when at least `threshold` zombies were killed during the level (Chapter Hunter).
public class KillCountCondition implements QuestCondition {
    private final int threshold;

    public KillCountCondition(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.getZombiesKilled() >= threshold;
    }
}
