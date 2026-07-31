package models.quests.QuestCondition;

import models.quests.QuestContext;

// ONE NAMED plant fells `threshold` zombies over the day (Pro Plant Player); no exclusivity clause.
public class KillWithSinglePlantCondition implements QuestCondition {
    private final String plantName;
    private final int threshold;

    public KillWithSinglePlantCondition(String plantName, int threshold) {
        this.plantName = plantName;
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.killsTodayByPlant(plantName) >= threshold;
    }

    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        int killed = profile == null ? 0 : profile.getKillsTodayByPlant(plantName);
        return models.quests.QuestProgress.crossLevel(killed, threshold);
    }
}
