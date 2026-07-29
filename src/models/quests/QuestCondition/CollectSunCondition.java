package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the player has banked at least `threshold` sun over the course of one calendar day
// (Daily Sun Catcher: "collect sun_amount sun during one day").
//
// The count is deliberately the whole day's, not the level's: the sun may be gathered over as many
// matches as the player likes, and it only goes back to zero when the date changes. The running total
// lives on the Profile (and is persisted), so it survives quitting and reloading mid-day.
public class CollectSunCondition implements QuestCondition {
    private final int threshold;

    public CollectSunCondition(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.getSunCollectedToday() >= threshold;
    }

    // Genuinely cross-level within the day: the total is kept on the profile and carries between
    // matches, so the travel log can show a real running tally towards the day's target.
    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        int collected = profile == null ? 0 : profile.getSunCollectedToday();
        return models.quests.QuestProgress.crossLevel(collected, threshold);
    }
}
