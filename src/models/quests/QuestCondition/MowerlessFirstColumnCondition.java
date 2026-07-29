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

    // The kills accumulate across every level played today and reset with the calendar day: ten
    // last-ditch kills in one match would mean losing ten mowers first, so the goal only makes sense
    // spread over several matches.
    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.getMowerlessFirstColumnKillsToday() >= threshold;
    }

    // Cross-level within the day: the tally is kept on the profile between matches, so the travel log
    // shows how many of these last-ditch kills are already banked.
    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        int killed = profile == null ? 0 : profile.getMowerlessFirstColumnKillsToday();
        return models.quests.QuestProgress.crossLevel(killed, threshold);
    }
}
