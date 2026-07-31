package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when at least `threshold` zombies have been defeated in ONE NAMED chapter (Chapter Hunter:
// "defeat 50 zombies from chapter X"). The chapter is authored on the quest, so grinding a different
// chapter does nothing for it -- the player has to go and fight the one it names.
//
// The kills accumulate: the per-chapter totals live on the profile and carry between matches, so they
// can be spread over as many plays of that chapter as it takes.
public class ChapterKillCountCondition implements QuestCondition {
    private final String chapter;
    private final int threshold;

    public ChapterKillCountCondition(String chapter, int threshold) {
        this.chapter = chapter;
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.killsInChapter(chapter) >= threshold;
    }

    // The running tally for the named chapter, so the travel log shows real progress towards it.
    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        int killed = profile == null ? 0 : profile.getChapterZombieKills(chapter);
        return models.quests.QuestProgress.crossLevel(killed, threshold);
    }
}
