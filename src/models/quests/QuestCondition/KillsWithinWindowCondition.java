package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when at least `threshold` zombies were killed within 30 seconds of the first wave
// starting (Quick Action). The window itself is tracked on the session as kills land.
public class KillsWithinWindowCondition implements QuestCondition {
    private final int threshold;

    public KillsWithinWindowCondition(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.getKillsInFirst30s() >= threshold;
    }
}
