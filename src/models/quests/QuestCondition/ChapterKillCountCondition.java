package models.quests.QuestCondition;

import models.quests.QuestContext;

// Satisfied when at least `threshold` zombies have been defeated across the levels of a single chapter
// (Chapter Hunter). Unlike a per-level kill count, this reads a running per-chapter total that the
// profile accumulates level by level (and persists), so the kills can be spread over several plays of
// the chapter.
public class ChapterKillCountCondition implements QuestCondition {
    private final int threshold;

    public ChapterKillCountCondition(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(QuestContext ctx) {
        return ctx.getChapterZombiesKilled() >= threshold;
    }

    // Genuinely cross-level: the per-chapter totals live on the profile and carry between matches, so
    // the travel log can show a real running tally. The best chapter is the one that will finish first.
    @Override
    public models.quests.QuestProgress progress(models.user.Profile profile) {
        int killed = profile == null ? 0 : profile.getBestChapterZombieKills();
        return models.quests.QuestProgress.crossLevel(killed, threshold);
    }
}
