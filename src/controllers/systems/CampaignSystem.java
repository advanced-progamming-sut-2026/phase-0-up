package controllers.systems;

import models.game.Chapter;
import models.game.Level;
import models.user.Profile;

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
            advanceToNextChapter(profile);
        }
    }

    // Advances the progress pointer to the next chapter's first level. The newly reached chapter is
    // materialized into the live campaign by LevelInitializer.attachCampaign on the next login.
    private void advanceToNextChapter(Profile profile) {
        bumpProgress(profile, profile.getLastChapter() + 1, 1);
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
