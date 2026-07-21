package models.leaderboard;

import models.user.Profile;
import models.user.User;
import utils.Constants;

// One immutable row of the leaderboard: a registered player's username plus the whole-game statistics
// the leaderboard ranks on. Built once from a User via from(User); it reads only plain scalar progress
// off the Profile, so no live game object (Quest/Chapter/Level/GameSession) is ever touched here.
public class LeaderboardEntry {
    private final String username;
    private final int lastChapter;
    private final int lastLevel;
    private final int minigamesCompleted;
    private final int dailyQuests;
    private final int nonDailyQuests;
    private final int bestMyoPoint;

    public LeaderboardEntry(String username, int lastChapter, int lastLevel, int minigamesCompleted,
                            int dailyQuests, int nonDailyQuests, int bestMyoPoint) {
        this.username = username;
        this.lastChapter = lastChapter;
        this.lastLevel = lastLevel;
        this.minigamesCompleted = minigamesCompleted;
        this.dailyQuests = dailyQuests;
        this.nonDailyQuests = nonDailyQuests;
        this.bestMyoPoint = bestMyoPoint;
    }

    // Snapshot a registered player's progression into a leaderboard row. passedMiniGames maps each
    // mini-game to how many times the player has cleared it, so the total number of successful
    // mini-games is the sum of those counts.
    public static LeaderboardEntry from(User user) {
        Profile p = user.getProfile();
        int minigames = 0;
        if (p.getPassedMiniGames() != null) {
            for (int wins : p.getPassedMiniGames().values()) {
                minigames += wins;
            }
        }
        return new LeaderboardEntry(
                user.getUsername(),
                p.getLastChapter(),
                p.getLastLevel(),
                minigames,
                p.getDailyQuestsDone(),
                p.getNoneDailyQuestsDone(),
                p.getBestNumberOfMeowPoints());
    }

    public String getUsername() {
        return username;
    }

    public int getLastChapter() {
        return lastChapter;
    }

    public int getLastLevel() {
        return lastLevel;
    }

    public int getMinigamesCompleted() {
        return minigamesCompleted;
    }

    public int getDailyQuests() {
        return dailyQuests;
    }

    public int getNonDailyQuests() {
        return nonDailyQuests;
    }

    public int getBestMyoPoint() {
        return bestMyoPoint;
    }

    // Number of campaign levels the player has actually finished. lastChapter/lastLevel point at the
    // next unlocked level (see CampaignSystem.bumpProgress), so the finished count is one behind them.
    public int completedLevels() {
        int count = (lastChapter - 1) * Constants.LEVELS_PER_CHAPTER + (lastLevel - 1);
        return Math.max(count, 0);
    }

    // The last stage the player actually completed, as "chapter-stage" (e.g. "1-3"), matching the
    // documentation's "Stage 1-3" example. Derived from completedLevels() so it stays one behind the
    // next-unlocked pointer; a brand-new profile that has cleared nothing reads as "-".
    public String getStageLabel() {
        int completed = completedLevels();
        if (completed <= 0) {
            return "-";
        }
        int chapter = (completed - 1) / Constants.LEVELS_PER_CHAPTER + 1;
        int stage = (completed - 1) % Constants.LEVELS_PER_CHAPTER + 1;
        return chapter + "-" + stage;
    }
}
