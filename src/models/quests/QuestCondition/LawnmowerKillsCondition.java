package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied once at least `threshold` zombies have been killed by lawn mowers (Mowing Time). The count
// is the account's lifetime total, not one level's: an epic challenge asking for up to 50 mower kills
// is a long-haul goal, and a single level only ever has one mower per row to spend.
public class LawnmowerKillsCondition implements QuestCondition {
    private final int threshold;

    public LawnmowerKillsCondition(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.getLawnmowerKillsTotal() >= threshold;
    }

    // Cross-level and lifetime: the total is kept on the profile between matches, so the travel log
    // shows a running tally that carries forward for as long as it takes.
    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        int killed = profile == null ? 0 : profile.getLawnmowerKillsTotal();
        return models.quests.QuestProgress.crossLevel(killed, threshold);
    }
}
