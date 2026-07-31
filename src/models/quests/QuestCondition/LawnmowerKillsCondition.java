package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied once `threshold` zombies die to lawn mowers (Mowing Time). Lifetime, not per level.
public class LawnmowerKillsCondition implements QuestCondition {
    private final int threshold;

    public LawnmowerKillsCondition(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.getLawnmowerKillsTotal() >= threshold;
    }

    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        int killed = profile == null ? 0 : profile.getLawnmowerKillsTotal();
        return models.quests.QuestProgress.crossLevel(killed, threshold);
    }
}
