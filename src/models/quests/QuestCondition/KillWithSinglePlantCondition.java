package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when ONE NAMED plant has felled at least `threshold` zombies over the day's play (Pro Plant
// Player: "kill ten zombies with X"). The plant is authored on the quest, so kills by anything else do
// not count towards it.
//
// The kills accumulate across levels and reset with the calendar day, so the ten need not all fall in
// one match. That is also why there is no "and nothing else got a kill" clause: held to a whole day it
// would forbid the player from using any other plant until the quest finished, which is not a quest so
// much as a hostage situation. Use KILL_WITH_PLANT for the strict single-level "only X" variant.
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

    // Cross-level within the day: the tally is kept on the profile between matches, so the travel log
    // shows how many of the required kills are already banked.
    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        int killed = profile == null ? 0 : profile.getKillsTodayByPlant(plantName);
        return models.quests.QuestProgress.crossLevel(killed, threshold);
    }
}
