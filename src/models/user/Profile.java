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
    public static final String DIAMONDS = "Diamonds";

    // Balance-change listener, registered once by the controller layer at start-up. Static, so it is
    // never serialized with a profile.
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
    // Live game objects, rebuilt from the registries at runtime and never persisted -- saving goes
    // exclusively through ProfileRecord, which lists only plain progress data.
    private List<Quest> activeQuests;
    private List<Quest> completedQuests;
    // Ids of the one-shot (MAIN / EPIC) quests already completed and rewarded. Permanent.
    private Set<String> completedQuestIds;
    // Ids of the DAILY quests completed *today*. Emptied when the calendar day turns, which is what
    // lets a daily quest be earned again tomorrow while a main/epic quest never re-fires.
    private Set<String> completedDailyQuestIds;
    // ISO-8601 day the daily state below belongs to; a null/stale stamp triggers a rollover on access.
    private String questDayStamp;
    // Sun banked today, across every level played on this calendar day (Daily Sun Catcher).
    private int sunCollectedToday;
    // Kills in column 0 of a mower-spent row today, across every level (Almost Victorious).
    private int mowerlessFirstColumnKillsToday;
    // Zombies felled today per killer plant, across every level, keyed by lower-cased plant name
    // (Pro Plant Player).
    private Map<String, Integer> killsByPlantToday;
    // Lifetime lawn-mower kills (Mowing Time). An epic is earned once, so this never rolls over.
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
    // Cross-level quest progress, persisted via ProfileRecord: the max-difficulty win streak (Win
    // After Win) and the zombies felled per chapter (Chapter Hunter).
    private int winStreakAtMaxDifficulty;
    private Map<String, Integer> zombieKillsByChapter;

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

    // The basic loadout every profile starts with -- without it no seed can be selected and no level
    // is playable. Further plants come from the shop (CollectionSystem.purchasePlant).
    private void initializeStartingPlants() {
        for (String plant : Constants.STARTING_PLANTS) {
            unlockPlant(plant);
            ownedSeedPackets.putIfAbsent(plant.toLowerCase().trim(), 1);   // idempotent
        }
    }

    // Re-grants the starter plants after a load: Gson sets fields directly and never runs the
    // constructor, so a saved profile would otherwise come back with every seed reading as locked.
    // Call once when a user becomes active (login / auto-login). Idempotent.
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

    // Wires the view-side balance listener; null detaches it.
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

    // Every coin/gem change funnels through the add/spend methods above, so the player sees the new
    // total whenever it moves, for any reason. The restore setters stay silent -- loading a save is
    // not a balance-change event. The model never prints: it notifies the observer the controller
    // registered at start-up and the view renders it, which is what keeps this MVC-clean and testable.
    private void reportCoins() { notifyBalance(COINS, coins); }

    private void reportGems() { notifyBalance(DIAMONDS, gems); }

    private void notifyBalance(String currency, int newTotal) {
        CurrencyObserver observer = currencyObserver;
        if (observer != null) {
            observer.onBalanceChanged(currency, newTotal);
        }
    }

    public int getPlantFoodCount() { return plantFoodCount; }

    public void addPlantFood(int n) { this.plantFoodCount += n; }

    public void spendPlantFood(int n) { this.plantFoodCount -= n; }

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

    // Completion record for the one-shot quests; also the guard against paying a reward twice.
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

    // --- Daily quest state (rolls over at midnight, local calendar day) ---------------------------

    // Today's date, in one place, so the rollover and the stored stamp always agree.
    private static String today() {
        return java.time.LocalDate.now().toString();
    }

    // Rolls the daily state forward when the stored stamp is not today. Every daily reader and writer
    // below calls it, so a session spanning midnight -- or a profile loaded days later -- sees a clean
    // day with nothing polling a clock. It deliberately leaves the lifetime tallies alone
    // (dailyQuestsDone / noneDailyQuestsDone): those are what the leaderboard ranks on.
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
        // Same cadence, and it had no other reset -- once bought it stayed bought forever.
        hasBoughtDailyOfferToday = false;
    }

    public Set<String> getCompletedDailyQuestIds() {
        if (completedDailyQuestIds == null) {   // a profile deserialized before this field existed
            completedDailyQuestIds = new HashSet<>();
        }
        return completedDailyQuestIds;
    }

    // Whether this daily quest is already earned today; tomorrow it reads false again.
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

    // Sun banked today across every level played (Daily Sun Catcher). Reads 0 on a new day, because
    // the rollover runs first.
    public int getSunCollectedToday() {
        ensureQuestDay();
        return sunCollectedToday;
    }

    // Credits a fresh sun pickup and returns the new total, so a caller can react to it crossing a
    // quest threshold without a second lookup.
    public int addSunCollectedToday(int amount) {
        ensureQuestDay();
        if (amount > 0) {
            sunCollectedToday += amount;
        }
        return sunCollectedToday;
    }

    // Kills in column 0 of a mower-spent row today, across every level (Almost Victorious).
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

    // Zombies one plant type has felled today, across every level (Pro Plant Player).
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

    // Lifetime lawn-mower kills (Mowing Time) -- no rollover, an epic is earned once.
    public int getLawnmowerKillsTotal() { return lawnmowerKillsTotal; }

    public void addLawnmowerKills(int amount) {
        if (amount > 0) {
            lawnmowerKillsTotal += amount;
        }
    }

    public void setLawnmowerKillsTotal(int total) { this.lawnmowerKillsTotal = total; }

    // --- Restore accessors for the daily state (used when rebuilding a profile from its record) ---
    // The raw getters skip the rollover on purpose: it happens on the first real read instead, so a
    // save written on an earlier day correctly reads back as 0.

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

    // Unlocks a plant. Returns true only on a genuine first-time unlock, so callers can post a "New
    // Plant Unlocked" news entry without it firing again on a re-unlock or on the starter plants.
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
