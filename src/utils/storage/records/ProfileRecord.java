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
