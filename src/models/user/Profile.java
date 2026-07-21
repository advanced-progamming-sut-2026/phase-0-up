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

    // Listens for balance changes on every profile. Registered once by the controller layer at start-up
    // (Main) and backed by a view renderer; transient/static so it is never serialized with a profile.
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
    // Quests and chapters are live game objects rebuilt from the registries at runtime; they are never
    // persisted. Persistence goes exclusively through ProfileRecord, which lists only plain progress
    // data, so these (and the Level -> Wave -> Zombie -> GameSession graph they reach) can't leak into
    // a save file. Runtime progress is tracked by the scalar/id fields (lastChapter, lastLevel, ...).
    private List<Quest> activeQuests;
    private List<Quest> completedQuests;
    // Ids of quests already completed (and rewarded). Plain strings, so unlike the live Quest objects
    // above this is safe to persist alongside the other scalar progress data.
    private Set<String> completedQuestIds;
    private int lastChapter;
    private int lastLevel;
    private List<Chapter> unlockedChapters;
    private Chapter currentChapter;
    private Map<String, Integer> passedMiniGames;
    private int dailyQuestsDone;
    private int noneDailyQuestsDone;
    private boolean hasBoughtDailyOfferToday;
    private Set<String> seenZombieAliases;
    // Cross-level quest progress. Unlike the per-level tally the QuestSystem keeps in memory, these
    // survive a save/reload (persisted via ProfileRecord): the running streak of wins at maximum
    // difficulty (Win After Win) and the zombies felled so far in each chapter (Chapter Hunter).
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

        this.newsList = new ArrayList<>();
        this.unlockedPlants = new ArrayList<>();
        this.lockedPlants = new ArrayList<>();
        this.activeQuests = new ArrayList<>();
        this.completedQuests = new ArrayList<>();
        this.completedQuestIds = new HashSet<>();
        this.unlockedChapters = new ArrayList<>();
        this.seenZombieAliases = new HashSet<>();
        this.boostedSeeds = new HashSet<>();

        this.ownedSeedPackets = new HashMap<>();
        this.plantsLevels = new HashMap<>();
        this.passedMiniGames = new HashMap<>();

        this.myGreenHouse = new GreenHouse();

        initializeStartingPlants();
    }

    // Every profile starts owning the basic loadout, which is exactly the pool the first level of
    // chapter 1 offers -- without these a new player cannot select any seed and no level is playable.
    // Further plants are unlocked through the shop (see CollectionSystem.buyPlant).
    private void initializeStartingPlants() {
        for (String plant : Constants.STARTING_PLANTS) {
            unlockPlant(plant);
            ownedSeedPackets.putIfAbsent(plant.toLowerCase().trim(), 1);   // idempotent
        }
    }

    // Grants the always-available starter plants if they are missing. Gson deserializes a saved
    // profile field-by-field and never runs the constructor, so a loaded profile would otherwise have
    // no unlocked plants at all and every seed would read as "locked" in the seed-selection menu.
    // Call this once when a user becomes active (login / auto-login). Idempotent.
    public void ensureStartingPlants() {
        if (ownedSeedPackets == null) ownedSeedPackets = new java.util.HashMap<>();
        if (plantsLevels == null) plantsLevels = new java.util.HashMap<>();
        if (unlockedPlants == null) unlockedPlants = new java.util.ArrayList<>();
        if (lockedPlants == null) lockedPlants = new java.util.ArrayList<>();
        initializeStartingPlants();
    }

    // --- Getters & Setters ---

    public int getGameNumbers() {
        return gameNumbers;
    }

    public void increaseGameNumbers() {
        this.gameNumbers++;
    }

    // Wires the view-side listener for balance changes. Called by the controller layer at start-up;
    // passing null detaches it (the model then simply publishes to nobody).
    public static void setCurrencyObserver(CurrencyObserver observer) {
        currencyObserver = observer;
    }

    public int getCoins() {
        return coins;
    }

    public void addCoins(int n) {
        this.coins += n;
        reportCoins();
    }

    public void spendCoins(int n) {
        this.coins -= n;
        reportCoins();
    }

    public int getGems() {
        return gems;
    }

    public void addGems(int n) {
        this.gems += n;
        reportGems();
    }

    public void spendGems(int n) {
        this.gems -= n;
        reportGems();
    }

    // Every coin/diamond change funnels through the add/spend methods above, so publishing the new
    // balance here means the player is shown the updated total whenever it changes, for any reason
    // (shop, rewards, quests, cheats, ...). The setters used to restore a saved profile stay silent --
    // loading a save is not a balance-change event.
    //
    // The model does NOT print: it notifies the observer the controller registered at start-up, and the
    // view renders it (see CurrencyObserver / CurrencyRenderer). With no observer registered nothing is
    // emitted and the model behaves identically, which is what keeps this testable and MVC-clean.
    private void reportCoins() {
        notifyBalance(COINS, coins);
    }

    private void reportGems() {
        notifyBalance(DIAMONDS, gems);
    }

    private void notifyBalance(String currency, int newTotal) {
        CurrencyObserver observer = currencyObserver;
        if (observer != null) {
            observer.onBalanceChanged(currency, newTotal);
        }
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

    // Per-quest completion record. A completed quest has already had its reward granted, so this also
    // guards against granting the same reward twice.
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

    // --- Cross-level quest progress (Win After Win, Chapter Hunter) -------------------------------

    public int getWinStreakAtMaxDifficulty() {
        return winStreakAtMaxDifficulty;
    }

    public void setWinStreakAtMaxDifficulty(int winStreakAtMaxDifficulty) {
        this.winStreakAtMaxDifficulty = winStreakAtMaxDifficulty;
    }

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

    // Unlocks a plant. Returns true only when it was newly unlocked (previously locked), so callers
    // can react to a genuine first-time unlock -- e.g. post a "New Plant Unlocked" news entry -- without
    // firing again on a re-unlock or on the starter plants granted at profile creation.
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

    public void setGameNumbers(int gameNumbers) {
        this.gameNumbers = gameNumbers;
    }

    public void setCoins(int coins) {
        this.coins = coins;
    }

    public void setGems(int gems) {
        this.gems = gems;
    }

    public void setPlantFoodCount(int plantFoodCount) {
        this.plantFoodCount = plantFoodCount;
    }

    public void setDailyQuestsDone(int dailyQuestsDone) {
        this.dailyQuestsDone = dailyQuestsDone;
    }

    public void setNoneDailyQuestsDone(int noneDailyQuestsDone) {
        this.noneDailyQuestsDone = noneDailyQuestsDone;
    }

    public void setMyGreenHouse(GreenHouse myGreenHouse) {
        this.myGreenHouse = myGreenHouse;
    }
}
