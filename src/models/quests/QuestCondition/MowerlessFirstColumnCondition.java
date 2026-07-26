package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when at least `threshold` zombies were killed in column 0 (the tile nearest the house) of
// a row whose lawn mower has already been spent (Almost Victorious). Those are the last-ditch kills a
// row makes with no mower left to fall back on. Mower kills themselves don't count -- the tally only
// credits plant kills there.
public class MowerlessFirstColumnCondition implements QuestCondition {
    private final int threshold;

    public MowerlessFirstColumnCondition(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.getMowerlessFirstColumnKills() >= threshold;
    }

    // A single-level goal: nothing carries between matches, so the travel log shows the
    // target with no running tally rather than implying progress that would not persist.
    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        return models.quests.QuestProgress.perLevel(threshold);
    }
}
