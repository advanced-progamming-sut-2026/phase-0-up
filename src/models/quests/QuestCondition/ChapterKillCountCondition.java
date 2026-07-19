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
}
