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
    // Boxed: null means the player has never finished a scoring-game run, which is not the same fact
    // as scoring zero. See Profile.bestNumberOfMeowPoints.
    private Integer bestNumberOfMeowPoints;
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
    private int versusWins;                  // two-player "I, Zombie" record (Phase 3)
    private int versusLosses;
    private Map<String, Integer> zombieKillsByChapter;   // Chapter Hunter: kills tallied per chapter
    // Daily quest state, saved with its own day stamp so the same day resumes and an older one rolls over.
    private Set<String> completedDailyQuestIds;
    private String questDayStamp;
    private int sunCollectedToday;
    private int mowerlessFirstColumnKillsToday;                 // Almost Victorious
    private Map<String, Integer> killsByPlantToday;             // Pro Plant Player
    private int lawnmowerKillsTotal;   // Mowing Time -- lifetime, not daily, so it never rolls over
    // Settings. Plain values, so a save file written before they existed loads with Java's defaults
    // (0/false) and Profile.getGameSpeed clamps the 0 back to 1.
    private int gameSpeed;
    private boolean showGrid;
    private boolean debugMode;
    // Boxed, unlike the three above: 0 is a legal volume (mute), so it cannot double as "absent from
    // an older save" the way gameSpeed's 0 does. See Profile.volume.
    private Integer volume;

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
        r.hasBoughtDailyOfferToday = p.getRawHasBoughtDailyOfferToday();
        r.seenZombieAliases = new HashSet<>(p.getSeenZombieAliases());
        r.completedQuestIds = new HashSet<>(p.getCompletedQuestIds());
        r.winStreakAtMaxDifficulty = p.getWinStreakAtMaxDifficulty();
        r.versusWins = p.getVersusWins();
        r.versusLosses = p.getVersusLosses();
        r.zombieKillsByChapter = new HashMap<>(p.getZombieKillsByChapter());
        r.completedDailyQuestIds = new HashSet<>(p.getCompletedDailyQuestIds());
        r.questDayStamp = p.getQuestDayStamp();
        r.sunCollectedToday = p.getRawSunCollectedToday();
        r.mowerlessFirstColumnKillsToday = p.getRawMowerlessFirstColumnKillsToday();
        r.killsByPlantToday = new HashMap<>(p.getKillsByPlantToday());
        r.lawnmowerKillsTotal = p.getLawnmowerKillsTotal();
        r.gameSpeed = p.getGameSpeed();
        r.showGrid = p.isShowGrid();
        r.debugMode = p.isDebugMode();
        r.volume = p.getVolume();
        return r;
    }

    public Profile toProfile() {
        Profile p = new Profile();
        p.setGameNumbers(gameNumbers);
        p.setCoins(coins);
        p.setGems(gems);
        p.setPlantFoodCount(plantFoodCount);
        p.setDifficultyLevel(difficultyLevel);
        // A save written before the field was boxed stores a literal 0 for every player who never
        // touched the scoring game -- the old default, which nobody earned. Loading that as a real
        // score would put a fake 0 in the leaderboard's "My Point" column for every legacy account,
        // which is precisely what the spec forbids, so a stored 0 is read as "never played".
        //
        // The cost, stated rather than hidden: a player who genuinely finished a run scoring exactly 0
        // is treated as never having played. That is a run with no kills and no leftover sun, and the
        // two are indistinguishable to the player anyway -- both display as "-", and any later score
        // beats both.
        p.setBestNumberOfMeowPoints(
                bestNumberOfMeowPoints == null || bestNumberOfMeowPoints == 0
                        ? null : bestNumberOfMeowPoints);
        p.setVersusWins(versusWins);
        p.setVersusLosses(versusLosses);
        p.setLastChapter(lastChapter);
        p.setLastLevel(lastLevel);
        p.setDailyQuestsDone(dailyQuestsDone);
        p.setNoneDailyQuestsDone(noneDailyQuestsDone);
        p.setHasBoughtDailyOfferToday(hasBoughtDailyOfferToday);
        p.setWinStreakAtMaxDifficulty(winStreakAtMaxDifficulty);
        // Stamp restored BEFORE the values, so a stale one makes the profile's rollover clear them.
        p.setQuestDayStamp(questDayStamp);
        p.setSunCollectedToday(sunCollectedToday);
        p.setMowerlessFirstColumnKillsToday(mowerlessFirstColumnKillsToday);
        p.setLawnmowerKillsTotal(lawnmowerKillsTotal);   // lifetime: restored as-is, never rolled over
        p.setGameSpeed(gameSpeed);
        p.setShowGrid(showGrid);
        p.setDebugMode(debugMode);
        // Left at the Profile's own default when the save predates the setting, rather than forced to
        // the 0 an unboxed field would have produced -- which is mute.
        if (volume != null) {
            p.setVolume(volume);
        }
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
        replace(p.getKillsByPlantToday(), killsByPlantToday);
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
