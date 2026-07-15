package models.user;

import models.game.Chapter;
import models.greenhouse.GreenHouse;
import models.news.News;
import models.quests.Quest;
import utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Profile {
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
    // Quests and chapters are live game objects rebuilt from the registries at runtime (their graph
    // reaches Level -> Wave -> Zombie, which holds a back-reference to GameSession and is not
    // serializable). They are marked transient so the user save only persists real progress data;
    // progress itself is tracked by the scalar/id fields (lastChapter, lastLevel, quest counters, ...).
    private transient List<Quest> activeQuests;
    private transient List<Quest> completedQuests;
    private int lastChapter;
    private int lastLevel;
    private transient List<Chapter> unlockedChapters;
    private transient Chapter currentChapter;
    private Map<String, Integer> passedMiniGames;
    private int dailyQuestsDone;
    private int noneDailyQuestsDone;
    private boolean hasBoughtDailyOfferToday;
    private Set<String> seenZombieAliases;

    public Profile() {
        this.gameNumbers = Constants.DEFAULT_GAME_NUMBERS;
        this.coins = Constants.DEFAULT_INITIAL_COINS;
        this.gems = Constants.DEFAULT_INITIAL_GEMS;
        this.plantFoodCount = Constants.DEFAULT_PLANT_FOOD_COUNT;
        this.difficultyLevel = Constants.DEFAULT_DIFFICULTY_LEVEL;
        this.bestNumberOfMeowPoints = Constants.DEFAULT_BEST_MEOW_POINTS;
        this.lastChapter = Constants.DEFAULT_LAST_CHAPTER;
        this.lastLevel = Constants.DEFAULT_LAST_LEVEL;
        this.dailyQuestsDone = Constants.DEFAULT_DAILY_QUESTS_DONE;
        this.noneDailyQuestsDone = Constants.DEFAULT_NONE_DAILY_QUESTS_DONE;
        this.hasBoughtDailyOfferToday = Constants.DEFAULT_HAS_BOUGHT_DAILY_OFFER;

        this.newsList = new ArrayList<>();
        this.unlockedPlants = new ArrayList<>();
        this.lockedPlants = new ArrayList<>();
        this.activeQuests = new ArrayList<>();
        this.completedQuests = new ArrayList<>();
        this.unlockedChapters = new ArrayList<>();
        this.seenZombieAliases = new HashSet<>();
        this.boostedSeeds = new HashSet<>();

        this.ownedSeedPackets = new HashMap<>();
        this.plantsLevels = new HashMap<>();
        this.passedMiniGames = new HashMap<>();

        this.myGreenHouse = new GreenHouse();

        initializeStartingPlants();
    }

    private void initializeStartingPlants() {
        //TODO: unlock default plants for each profile;
    }

    // --- Getters & Setters ---

    public int getGameNumbers() {
        return gameNumbers;
    }

    public void increaseGameNumbers() {
        this.gameNumbers++;
    }

    public int getCoins() {
        return coins;
    }

    public void addCoins(int n) {
        this.coins += n;
    }

    public void spendCoins(int n) {
        this.coins -= n;
    }

    public int getGems() {
        return gems;
    }

    public void addGems(int n) {
        this.gems += n;
    }

    public void spendGems(int n) {
        this.gems -= n;
    }

    public int getPlantFoodCount() {
        return plantFoodCount;
    }

    public void addPlantFood(int n) {
        this.plantFoodCount += n;
    }

    public void spendPlantFood(int n) {
        this.plantFoodCount -= n;
    }

    public int getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(int difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    public int getBestNumberOfMeowPoints() {
        return bestNumberOfMeowPoints;
    }

    public void setBestNumberOfMeowPoints(int bestNumberOfMeowPoints) {
        this.bestNumberOfMeowPoints = bestNumberOfMeowPoints;
    }

    public List<News> getNewsList() {
        return newsList;
    }

    public void addNews(News news) {
        this.newsList.add(news);
    }

    public List<String> getUnlockedPlants() {
        return unlockedPlants;
    }

    public List<String> getLockedPlants() {
        return lockedPlants;
    }


    public Set<String> getSeenZombieAliases() {
        return seenZombieAliases;
    }

    public Map<String, Integer> getOwnedSeedPackets() {
        return ownedSeedPackets;
    }

    public GreenHouse getMyGreenHouse() {
        return myGreenHouse;
    }

    public List<Quest> getActiveQuests() {
        return activeQuests;
    }

    public List<Quest> getCompletedQuests() {
        return completedQuests;
    }

    public int getLastChapter() {
        return lastChapter;
    }

    public void setLastChapter(int lastChapter) {
        this.lastChapter = lastChapter;
    }

    public int getLastLevel() {
        return lastLevel;
    }

    public void setLastLevel(int lastLevel) {
        this.lastLevel = lastLevel;
    }

    public List<Chapter> getUnlockedChapters() {
        return unlockedChapters;
    }

    public void addUnlockedChapter(Chapter chapter) {
        this.unlockedChapters.add(chapter);
    }

    public Chapter getCurrentChapter() {
        return currentChapter;
    }

    public void setCurrentChapter(Chapter currentChapter) {
        this.currentChapter = currentChapter;
    }

    public Map<String, Integer> getPassedMiniGames() {
        return passedMiniGames;
    }

    public int getDailyQuestsDone() {
        return dailyQuestsDone;
    }

    public void incrementDailyQuestsDone() {
        this.dailyQuestsDone++;
    }

    public int getNoneDailyQuestsDone() {
        return noneDailyQuestsDone;
    }

    public void incrementNoneDailyQuestsDone() {
        this.noneDailyQuestsDone++;
    }

    public boolean isHasBoughtDailyOfferToday() {
        return hasBoughtDailyOfferToday;
    }

    public void setHasBoughtDailyOfferToday(boolean hasBoughtDailyOfferToday) {
        this.hasBoughtDailyOfferToday = hasBoughtDailyOfferToday;
    }

    public void addSeedPackets(String plantName, int count) {
        if (plantName == null) return;
        String key = plantName.toLowerCase().trim();
        ownedSeedPackets.put(key, ownedSeedPackets.getOrDefault(key, 0) + count);
    }

    public Map<String, Integer> getPlantsLevels() {
        return plantsLevels;
    }

    public Set<String> getBoostedSeeds() {
        return boostedSeeds;
    }

    public boolean isSeedBoosted(String plantName) {
        if (plantName == null) return false;
        return boostedSeeds.contains(plantName.toLowerCase().trim());
    }

    public void setSeedBoosted(String plantName, boolean boosted) {
        if (plantName == null) return;
        String key = plantName.toLowerCase().trim();
        if (boosted) {
            boostedSeeds.add(key);
        } else {
            boostedSeeds.remove(key);
        }
    }

    public void unlockPlant(String plantName){
        String formattedName = plantName.toLowerCase().trim();

        if (!unlockedPlants.contains(formattedName)) {
            unlockedPlants.add(formattedName);
        }
        lockedPlants.remove(formattedName);
        plantsLevels.putIfAbsent(formattedName, 1);
    }

    public void levelUpPlant(String plantName) {
        String key = plantName.toLowerCase().trim();
        int currentLevel = plantsLevels.getOrDefault(key, 1);
        plantsLevels.put(key, currentLevel + 1);
    }
}
