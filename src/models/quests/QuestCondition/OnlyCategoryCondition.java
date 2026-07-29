package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when the level is won having placed no more than `count` plants of one category -- e.g.
// beating a level on only 3 sun producers (Cloudy Day).
//
// The cap is on that one category, NOT on the whole garden: the quest asks the player to win on a
// starved economy, and defending the lawn with other plants is exactly how they are meant to do it.
// This previously also demanded that every plant placed be a sun producer, which made the quest
// literally unwinnable -- a garden of nothing but sunflowers kills no zombies, so the level could
// never be won and the condition could never fire.
public class OnlyCategoryCondition implements QuestCondition {
    private final String category;
    private final int count;

    public OnlyCategoryCondition(String category, int count) {
        this.category = category;
        this.count = count;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        // At most `count` of the capped category, and at least one -- "win on 3 sun producers" is a
        // budget to stay inside, and a garden with none of them at all is a different feat entirely.
        int placed = ctx.plantedCategoryCount(category);
        return ctx.isWon() && placed > 0 && placed <= count;
    }

    // A single-level goal: nothing carries between matches, so the travel log shows the
    // target with no running tally rather than implying progress that would not persist.
    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        return models.quests.QuestProgress.perLevel(count);
    }
}
