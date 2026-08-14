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
    // Currency labels used when publishing a balance change.
    public static final String COINS = "Coins";
    public static final String GEMS = "Gems";

    private static CurrencyObserver currencyObserver;

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
    // Live game objects, never persisted: saving goes exclusively through ProfileRecord.
    private List<Quest> activeQuests;
    private List<Quest> completedQuests;
    private Set<String> completedQuestIds;
    private Set<String> completedDailyQuestIds;
    private String questDayStamp;
    private int sunCollectedToday;
    private int mowerlessFirstColumnKillsToday;
    private Map<String, Integer> killsByPlantToday;
    private int lawnmowerKillsTotal;
    private int lastChapter;
    private int lastLevel;
    private List<Chapter> unlockedChapters;
    private Chapter currentChapter;
    private Map<String, Integer> passedMiniGames;
    private int dailyQuestsDone;
    private int noneDailyQuestsDone;
    private boolean hasBoughtDailyOfferToday;
    private Set<String> seenZombieAliases;
    private int winStreakAtMaxDifficulty;
    private Map<String, Integer> zombieKillsByChapter;

    // Player preferences. They live on the Profile rather than in the view because they are per-account
    // and have to survive a restart, which means going through ProfileRecord like everything else.
    //
    // gameSpeed multiplies the fixed-step accumulator's rate, so it changes how fast the model ticks
    // rather than how fast it is drawn -- the terminal build has no use for it, but it costs nothing
    // there and keeping one Profile shape for both builds is worth more than the saved field.
    private int gameSpeed;
    private boolean showGrid;
    private boolean debugMode;

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
        this.gameSpeed = Constants.DEFAULT_GAME_SPEED;
        this.winStreakAtMaxDifficulty = 0;
        this.zombieKillsByChapter = new HashMap<>();
        this.questDayStamp = today();
        this.sunCollectedToday = 0;
        this.mowerlessFirstColumnKillsToday = 0;
        this.lawnmowerKillsTotal = 0;
        this.killsByPlantToday = new HashMap<>();

        this.newsList = new ArrayList<>();
        this.unlockedPlants = new ArrayList<>();
        this.lockedPlants = new ArrayList<>();
        this.activeQuests = new ArrayList<>();
        this.completedQuests = new ArrayList<>();
        this.completedQuestIds = new HashSet<>();
        this.completedDailyQuestIds = new HashSet<>();
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
        for (String plant : Constants.STARTING_PLANTS) {
            unlockPlant(plant);
            ownedSeedPackets.putIfAbsent(plant.toLowerCase().trim(), 1);   // idempotent
        }
    }

    // Re-grants the starter plants after a load: Gson never runs the constructor. Idempotent.
    public void ensureStartingPlants() {
        if (ownedSeedPackets == null) ownedSeedPackets = new java.util.HashMap<>();
        if (plantsLevels == null) plantsLevels = new java.util.HashMap<>();
        if (unlockedPlants == null) unlockedPlants = new java.util.ArrayList<>();
        if (lockedPlants == null) lockedPlants = new java.util.ArrayList<>();
        initializeStartingPlants();
    }

    // --- Getters & Setters ---

    public int getGameNumbers() { return gameNumbers; }

    public void increaseGameNumbers() { this.gameNumbers++; }

    public static void setCurrencyObserver(CurrencyObserver observer) { currencyObserver = observer; }

    public int getCoins() { return coins; }

    public void addCoins(int n) {
        this.coins += n;
        reportCoins();
    }

    public void spendCoins(int n) {
        this.coins -= n;
        reportCoins();
    }

    public int getGems() { return gems; }

    public void addGems(int n) {
        this.gems += n;
        reportGems();
    }

    public void spendGems(int n) {
        this.gems -= n;
        reportGems();
    }

    private void reportCoins() { notifyBalance(COINS, coins); }

    private void reportGems() { notifyBalance(GEMS, gems); }

    private void notifyBalance(String currency, int newTotal) {
        CurrencyObserver observer = currencyObserver;
        if (observer != null) {
            observer.onBalanceChanged(currency, newTotal);
        }
    }

    public int getPlantFoodCount() { return plantFoodCount; }

    public void addPlantFood(int n) { this.plantFoodCount += n; }

    public void spendPlantFood(int n) { this.plantFoodCount -= n; }

    // Clamped on read, not on write: a save file from before this field existed deserialises it as 0,
    // and a game that ticks zero times a second looks exactly like a freeze.
    public int getGameSpeed() {
        return gameSpeed < Constants.MIN_GAME_SPEED || gameSpeed > Constants.MAX_GAME_SPEED
                ? Constants.DEFAULT_GAME_SPEED
                : gameSpeed;
    }

    public void setGameSpeed(int gameSpeed) { this.gameSpeed = gameSpeed; }

    public boolean isShowGrid() { return showGrid; }

    public void setShowGrid(boolean showGrid) { this.showGrid = showGrid; }

    public boolean isDebugMode() { return debugMode; }

    public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }

    public int getDifficultyLevel() { return difficultyLevel; }

    public void setDifficultyLevel(int difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public int getBestNumberOfMeowPoints() { return bestNumberOfMeowPoints; }

    public void setBestNumberOfMeowPoints(int best) { this.bestNumberOfMeowPoints = best; }

    public List<News> getNewsList() { return newsList; }

    public void addNews(News news) { this.newsList.add(news); }

    public List<String> getUnlockedPlants() { return unlockedPlants; }

    public List<String> getLockedPlants() { return lockedPlants; }

    public Set<String> getSeenZombieAliases() { return seenZombieAliases; }

    public Map<String, Integer> getOwnedSeedPackets() { return ownedSeedPackets; }

    public GreenHouse getMyGreenHouse() { return myGreenHouse; }

    public List<Quest> getActiveQuests() { return activeQuests; }

    public List<Quest> getCompletedQuests() { return completedQuests; }

    public int getLastChapter() { return lastChapter; }

    public void setLastChapter(int lastChapter) { this.lastChapter = lastChapter; }
    public int getLastLevel() { return lastLevel; }
    public void setLastLevel(int lastLevel) { this.lastLevel = lastLevel; }
    public List<Chapter> getUnlockedChapters() { return unlockedChapters; }
    public void addUnlockedChapter(Chapter chapter) { this.unlockedChapters.add(chapter); }
    public Chapter getCurrentChapter() { return currentChapter; }
    public void setCurrentChapter(Chapter currentChapter) { this.currentChapter = currentChapter; }
    public Map<String, Integer> getPassedMiniGames() { return passedMiniGames; }
    public int getDailyQuestsDone() { return dailyQuestsDone; }
    public void incrementDailyQuestsDone() { this.dailyQuestsDone++; }
    public int getNoneDailyQuestsDone() { return noneDailyQuestsDone; }
    public void incrementNoneDailyQuestsDone() { this.noneDailyQuestsDone++; }
    public Set<String> getCompletedQuestIds() {
        if (completedQuestIds == null) {   // a profile deserialized before this field existed
            completedQuestIds = new HashSet<>();
        }
        return completedQuestIds;
    }

    public boolean hasCompletedQuest(String questId) {
        return questId != null && getCompletedQuestIds().contains(questId);
    }

    public void markQuestCompleted(String questId) {
        if (questId != null) {
            getCompletedQuestIds().add(questId);
        }
    }

    private static String today() {
        return java.time.LocalDate.now().toString();
    }

    // Rolls the daily state forward on a date change; leaves the lifetime tallies the leaderboard ranks on.
    private void ensureQuestDay() {
        String today = today();
        if (today.equals(questDayStamp)) {
            return;
        }
        questDayStamp = today;
        sunCollectedToday = 0;
        mowerlessFirstColumnKillsToday = 0;
        getKillsByPlantToday().clear();
        getCompletedDailyQuestIds().clear();
        hasBoughtDailyOfferToday = false;
    }

    public Set<String> getCompletedDailyQuestIds() {
        if (completedDailyQuestIds == null) {   // a profile deserialized before this field existed
            completedDailyQuestIds = new HashSet<>();
        }
        return completedDailyQuestIds;
    }

    public boolean hasCompletedDailyQuestToday(String questId) {
        if (questId == null) {
            return false;
        }
        ensureQuestDay();
        return getCompletedDailyQuestIds().contains(questId);
    }

    public void markDailyQuestCompletedToday(String questId) {
        if (questId == null) {
            return;
        }
        ensureQuestDay();
        getCompletedDailyQuestIds().add(questId);
    }

    public int getSunCollectedToday() {
        ensureQuestDay();
        return sunCollectedToday;
    }

    public int addSunCollectedToday(int amount) {
        ensureQuestDay();
        if (amount > 0) {
            sunCollectedToday += amount;
        }
        return sunCollectedToday;
    }

    public int getMowerlessFirstColumnKillsToday() {
        ensureQuestDay();
        return mowerlessFirstColumnKillsToday;
    }

    public void addMowerlessFirstColumnKillsToday(int amount) {
        ensureQuestDay();
        if (amount > 0) {
            mowerlessFirstColumnKillsToday += amount;
        }
    }

    public int getKillsTodayByPlant(String plantName) {
        if (plantName == null) {
            return 0;
        }
        ensureQuestDay();
        return getKillsByPlantToday().getOrDefault(plantName.toLowerCase().trim(), 0);
    }

    public void addKillsTodayByPlant(String plantName, int amount) {
        if (plantName == null || plantName.isBlank() || amount <= 0) {
            return;
        }
        ensureQuestDay();
        getKillsByPlantToday().merge(plantName.toLowerCase().trim(), amount, Integer::sum);
    }

    public Map<String, Integer> getKillsByPlantToday() {
        if (killsByPlantToday == null) {   // a profile deserialized before this field existed
            killsByPlantToday = new HashMap<>();
        }
        return killsByPlantToday;
    }

    public int getLawnmowerKillsTotal() { return lawnmowerKillsTotal; }
    public void addLawnmowerKills(int amount) {
        if (amount > 0) {
            lawnmowerKillsTotal += amount;
        }
    }

    public void setLawnmowerKillsTotal(int total) { this.lawnmowerKillsTotal = total; }
    // Raw getters skip the rollover on purpose; it happens on the first real read instead.
    public int getRawMowerlessFirstColumnKillsToday() { return mowerlessFirstColumnKillsToday; }
    public void setMowerlessFirstColumnKillsToday(int kills) { this.mowerlessFirstColumnKillsToday = kills; }
    public String getQuestDayStamp() { return questDayStamp; }
    public void setQuestDayStamp(String questDayStamp) { this.questDayStamp = questDayStamp; }
    public int getRawSunCollectedToday() { return sunCollectedToday; }
    public void setSunCollectedToday(int sunCollectedToday) { this.sunCollectedToday = sunCollectedToday; }
    // --- Cross-level quest progress (Win After Win, Chapter Hunter) -------------------------------

    public int getWinStreakAtMaxDifficulty() { return winStreakAtMaxDifficulty; }

    public void setWinStreakAtMaxDifficulty(int streak) { this.winStreakAtMaxDifficulty = streak; }

    // A level just ended: a win at maximum difficulty extends the streak, anything else breaks it.
    public void recordLevelForWinStreak(boolean won, boolean atMaxDifficulty) {
        if (won && atMaxDifficulty) {
            winStreakAtMaxDifficulty++;
        } else {
            winStreakAtMaxDifficulty = 0;
        }
    }

    public Map<String, Integer> getZombieKillsByChapter() {
        if (zombieKillsByChapter == null) {   // a profile deserialized before this field existed
            zombieKillsByChapter = new HashMap<>();
        }
        return zombieKillsByChapter;
    }

    // Credits kills to a chapter's running total and returns the new total, so callers can react to
    // it reaching a quest threshold without a second lookup.
    public int addChapterZombieKills(String chapter, int amount) {
        if (chapter == null || amount <= 0) {
            return getChapterZombieKills(chapter);
        }
        String key = chapter.toLowerCase().trim();
        int total = getZombieKillsByChapter().getOrDefault(key, 0) + amount;
        getZombieKillsByChapter().put(key, total);
        return total;
    }

    public int getChapterZombieKills(String chapter) {
        if (chapter == null) {
            return 0;
        }
        return getZombieKillsByChapter().getOrDefault(chapter.toLowerCase().trim(), 0);
    }

    public boolean isHasBoughtDailyOfferToday() { return hasBoughtDailyOfferToday; }

    public void setHasBoughtDailyOfferToday(boolean bought) { this.hasBoughtDailyOfferToday = bought; }

    public void addSeedPackets(String plantName, int count) {
        if (plantName == null) return;
        String key = plantName.toLowerCase().trim();
        ownedSeedPackets.put(key, ownedSeedPackets.getOrDefault(key, 0) + count);
    }

    public Map<String, Integer> getPlantsLevels() { return plantsLevels; }

    public Set<String> getBoostedSeeds() { return boostedSeeds; }

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

    public boolean unlockPlant(String plantName){
        String formattedName = plantName.toLowerCase().trim();

        boolean newlyUnlocked = !unlockedPlants.contains(formattedName);
        if (newlyUnlocked) {
            unlockedPlants.add(formattedName);
        }
        lockedPlants.remove(formattedName);
        plantsLevels.putIfAbsent(formattedName, 1);
        ownedSeedPackets.put(formattedName, ownedSeedPackets.getOrDefault(formattedName, 1));
        return newlyUnlocked;
    }

    public void levelUpPlant(String plantName) {
        String key = plantName.toLowerCase().trim();
        int currentLevel = plantsLevels.getOrDefault(key, 1);
        plantsLevels.put(key, currentLevel + 1);
    }

    // --- Restore setters (used when rebuilding a profile from its saved record) ---

    public void setGameNumbers(int gameNumbers) { this.gameNumbers = gameNumbers; }

    public void setCoins(int coins) { this.coins = coins; }

    public void setGems(int gems) { this.gems = gems; }

    public void setPlantFoodCount(int plantFoodCount) { this.plantFoodCount = plantFoodCount; }

    public void setDailyQuestsDone(int dailyQuestsDone) { this.dailyQuestsDone = dailyQuestsDone; }

    public void setNoneDailyQuestsDone(int done) { this.noneDailyQuestsDone = done; }

    public void setMyGreenHouse(GreenHouse myGreenHouse) { this.myGreenHouse = myGreenHouse; }
}
