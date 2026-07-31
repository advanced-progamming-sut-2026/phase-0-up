package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when `threshold` zombies fall in ONE NAMED chapter (Chapter Hunter); totals accumulate.
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

    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        int killed = profile == null ? 0 : profile.getChapterZombieKills(chapter);
        return models.quests.QuestProgress.crossLevel(killed, threshold);
    }
}
