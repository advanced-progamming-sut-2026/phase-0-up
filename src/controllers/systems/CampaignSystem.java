package controllers.systems;

import models.game.Chapter;
import models.game.Level;
import models.user.Profile;
import utils.gameinitializers.LevelInitializer;

// Progression rules: completing a level marks it done, unlocks the next level (or the first level of
// the next chapter), and advances the profile's persisted lastChapter/lastLevel pointers. The live
// Chapter/Level graph is rebuilt from these pointers at login by LevelInitializer.
public class CampaignSystem {
    private static CampaignSystem instance;

    private CampaignSystem() { }

    public static synchronized CampaignSystem getInstance() {
        if (instance == null) {
            instance = new CampaignSystem();
        }
        return instance;
    }

    public void completeLevel(Profile profile, Level level) {
        if (profile == null || level == null) {
            return;
        }
        level.setCompleted(true);
        Chapter chapter = profile.getCurrentChapter();
        if (chapter == null) {
            return;
        }
        unlockNext(profile, chapter, level);
    }

    private void unlockNext(Profile profile, Chapter chapter, Level level) {
        Level[] levels = chapter.getLevels();
        int index = indexOf(levels, level);
        if (index < 0) {
            return;
        }
        if (index + 1 < levels.length) {
            levels[index + 1].setUnlocked(true);
            bumpProgress(profile, profile.getLastChapter(), index + 2);
        } else {
            // Last level of the chapter cleared -> open the first level of the next chapter.
            advanceToNextChapter(profile, levels.length);
        }
    }

    // Last level of a chapter cleared: advance the pointer, then rebuild the campaign so the newly
    // reached chapter is unlocked and current straight away rather than only after the next login.
    private void advanceToNextChapter(Profile profile, int levelsInChapter) {
        int next = profile.getLastChapter() + 1;
        if (next > LevelInitializer.chapterCount()) {
            // Final level of the final chapter. There is no next chapter, and lastChapter is
            // persisted -- a value with no chapter behind it corrupts every later campaign rebuild.
            // Park the pointer one past the last level instead, so the campaign reads as fully
            // cleared rather than as "sitting on the last level, not yet beaten".
            bumpProgress(profile, profile.getLastChapter(), levelsInChapter + 1);
            return;
        }
        bumpProgress(profile, next, 1);
        if (profile.getLastChapter() == next) {
            LevelInitializer.attachCampaign(profile);
        }
    }

    // Only advances pointers; never moves them backwards on a replay of an earlier level.
    private void bumpProgress(Profile profile, int chapterNumber, int levelNumber) {
        if (chapterNumber > profile.getLastChapter()
                || (chapterNumber == profile.getLastChapter() && levelNumber > profile.getLastLevel())) {
            profile.setLastChapter(chapterNumber);
            profile.setLastLevel(levelNumber);
        }
    }

    public boolean canEnter(Profile profile, Level level) {
        return level != null && level.isUnlocked();
    }

    private int indexOf(Level[] levels, Level target) {
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == target) {
                return i;
            }
        }
        return -1;
    }
}
