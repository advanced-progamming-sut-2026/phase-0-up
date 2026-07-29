package utils.storage.records;

import models.greenhouse.GreenHouse;
import models.news.News;
import models.user.Profile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Plain-data snapshot of a Profile for persistence. This is the explicit save boundary: only progress
// data is listed here (currency, unlocked/owned plants, level map, counters, greenhouse, news), so a
// live game object (Quest/Chapter/Level/Wave/Zombie/GameSession/Random) can never leak into the save
// file. Field names match Profile so older save files load unchanged.
public class ProfileRecord {
    private int gameNumbers;
    private int coins;
    private int gems;
    private int plantFoodCount;
    private int difficultyLevel;
    private int bestNumberOfMeowPoints;
    private List<News> newsList;
    private List<String> unlockedPlants;
    private List<String> lockedPlants;
    private Map<String, Integer> ownedSeedPackets;
    private Map<String, Integer> plantsLevels;
    private Set<String> boostedSeeds;
    private GreenHouse myGreenHouse;
    private int lastChapter;
    private int lastLevel;
    private Map<String, Integer> passedMiniGames;
    private int dailyQuestsDone;
    private int noneDailyQuestsDone;
    private boolean hasBoughtDailyOfferToday;
    private Set<String> seenZombieAliases;
    private Set<String> completedQuestIds;   // plain ids of finished quests -> survive a save/reload
    private int winStreakAtMaxDifficulty;    // Win After Win: running streak, persisted across levels
    private Map<String, Integer> zombieKillsByChapter;   // Chapter Hunter: kills tallied per chapter
    // Daily quest state. It is saved with its own day stamp rather than being dropped, so quitting and
    // coming back the same day resumes the day in progress (a half-collected Daily Sun Catcher keeps
    // its sun) while a save from an earlier day rolls over to a clean slate on load.
    private Set<String> completedDailyQuestIds;
    private String questDayStamp;
    private int sunCollectedToday;
    private int mowerlessFirstColumnKillsToday;                 // Almost Victorious
    private int lawnmowerKillsTotal;   // Mowing Time -- lifetime, not daily, so it never rolls over

    public static ProfileRecord from(Profile p) {
        ProfileRecord r = new ProfileRecord();
        r.gameNumbers = p.getGameNumbers();
        r.coins = p.getCoins();
        r.gems = p.getGems();
        r.plantFoodCount = p.getPlantFoodCount();
        r.difficultyLevel = p.getDifficultyLevel();
        r.bestNumberOfMeowPoints = p.getBestNumberOfMeowPoints();
        r.newsList = new ArrayList<>(p.getNewsList());
        r.unlockedPlants = new ArrayList<>(p.getUnlockedPlants());
        r.lockedPlants = new ArrayList<>(p.getLockedPlants());
        r.ownedSeedPackets = new HashMap<>(p.getOwnedSeedPackets());
        r.plantsLevels = new HashMap<>(p.getPlantsLevels());
        r.boostedSeeds = new HashSet<>(p.getBoostedSeeds());
        r.myGreenHouse = p.getMyGreenHouse();
        r.lastChapter = p.getLastChapter();
        r.lastLevel = p.getLastLevel();
        r.passedMiniGames = new HashMap<>(p.getPassedMiniGames());
        r.dailyQuestsDone = p.getDailyQuestsDone();
        r.noneDailyQuestsDone = p.getNoneDailyQuestsDone();
        r.hasBoughtDailyOfferToday = p.isHasBoughtDailyOfferToday();
        r.seenZombieAliases = new HashSet<>(p.getSeenZombieAliases());
        r.completedQuestIds = new HashSet<>(p.getCompletedQuestIds());
        r.winStreakAtMaxDifficulty = p.getWinStreakAtMaxDifficulty();
        r.zombieKillsByChapter = new HashMap<>(p.getZombieKillsByChapter());
        r.completedDailyQuestIds = new HashSet<>(p.getCompletedDailyQuestIds());
        r.questDayStamp = p.getQuestDayStamp();
        r.sunCollectedToday = p.getRawSunCollectedToday();
        r.mowerlessFirstColumnKillsToday = p.getRawMowerlessFirstColumnKillsToday();
        r.lawnmowerKillsTotal = p.getLawnmowerKillsTotal();
        return r;
    }

    public Profile toProfile() {
        Profile p = new Profile();
        p.setGameNumbers(gameNumbers);
        p.setCoins(coins);
        p.setGems(gems);
        p.setPlantFoodCount(plantFoodCount);
        p.setDifficultyLevel(difficultyLevel);
        p.setBestNumberOfMeowPoints(bestNumberOfMeowPoints);
        p.setLastChapter(lastChapter);
        p.setLastLevel(lastLevel);
        p.setDailyQuestsDone(dailyQuestsDone);
        p.setNoneDailyQuestsDone(noneDailyQuestsDone);
        p.setHasBoughtDailyOfferToday(hasBoughtDailyOfferToday);
        p.setWinStreakAtMaxDifficulty(winStreakAtMaxDifficulty);
        // The day stamp is restored BEFORE the daily values, so a record from an earlier day carries a
        // stale stamp and the profile's own rollover clears the values on their first read. A record
        // written before this field existed has no stamp at all, which reads as stale for the same
        // reason -- so an old save can never resurrect yesterday's sun total or daily completions.
        p.setQuestDayStamp(questDayStamp);
        p.setSunCollectedToday(sunCollectedToday);
        p.setMowerlessFirstColumnKillsToday(mowerlessFirstColumnKillsToday);
        p.setLawnmowerKillsTotal(lawnmowerKillsTotal);   // lifetime: restored as-is, never rolled over
        if (myGreenHouse != null) {
            p.setMyGreenHouse(myGreenHouse);
        }
        replace(p.getNewsList(), newsList);
        replace(p.getUnlockedPlants(), unlockedPlants);
        replace(p.getLockedPlants(), lockedPlants);
        replace(p.getBoostedSeeds(), boostedSeeds);
        replace(p.getSeenZombieAliases(), seenZombieAliases);
        replace(p.getOwnedSeedPackets(), ownedSeedPackets);
        replace(p.getPlantsLevels(), plantsLevels);
        replace(p.getPassedMiniGames(), passedMiniGames);
        replace(p.getCompletedQuestIds(), completedQuestIds);
        replace(p.getCompletedDailyQuestIds(), completedDailyQuestIds);
        replace(p.getZombieKillsByChapter(), zombieKillsByChapter);
        return p;
    }

    private static <T> void replace(java.util.Collection<T> target, java.util.Collection<T> source) {
        if (source != null) {
            target.clear();
            target.addAll(source);
        }
    }

    private static <K, V> void replace(Map<K, V> target, Map<K, V> source) {
        if (source != null) {
            target.clear();
            target.putAll(source);
        }
    }
}
