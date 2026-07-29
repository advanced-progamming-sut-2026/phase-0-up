package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when at least `threshold` plants of a given category were placed during a single level,
// e.g. three EXPLOSIVE plants (Pro Demolisher). Deliberately single-level: the spreadsheet spells this
// one out as "use 3 explosive plants IN ONE LEVEL", unlike the other counting quests, which carry no
// such qualifier and do accumulate between matches.
//
// No win is required -- the quest asks the player to *use* the plants, and the sheet says nothing
// about surviving the level afterwards.
public class PlantCategoryCountCondition implements QuestCondition {
    private final String category;
    private final int threshold;

    public PlantCategoryCountCondition(String category, int threshold) {
        this.category = category;
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.plantedCategoryCount(category) >= threshold;
    }

    // A single-level goal: nothing carries between matches, so the travel log shows the target with no
    // running tally rather than implying progress that would not persist.
    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        return models.quests.QuestProgress.perLevel(threshold);
    }
}
