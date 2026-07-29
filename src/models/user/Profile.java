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
    // Ids of one-shot quests already completed (and rewarded) -- the MAIN and EPIC quests, which are
    // achievements and stay done forever. Plain strings, so unlike the live Quest objects above this
    // is safe to persist alongside the other scalar progress data.
    private Set<String> completedQuestIds;
    // Ids of the DAILY quests already completed *today*. Daily quests are repeatable by definition
    // ("quests that encourage the player to come back each day"), so this set -- and only this set --
    // is emptied when the calendar day turns. Keeping it apart from completedQuestIds is what lets a
    // daily quest be earned again tomorrow while a main/epic quest never re-fires.
    private Set<String> completedDailyQuestIds;
    // The calendar day (ISO-8601, e.g. "2026-07-30") the daily state above belongs to. A null/stale
    // stamp means the daily state is from an earlier day and is rolled over on next access.
    private String questDayStamp;
    // Sun banked so far today, across every level played on this calendar day (Daily Sun Catcher).
    private int sunCollectedToday;
    // Kills in column 0 of a mower-spent row today, across every level (Almost Victorious).
    private int mowerlessFirstColumnKillsToday;
    // Zombies felled by lawn mowers over the whole account's life (Mowing Time). Epic challenges are
    // one-off achievements, so unlike the two above this is a lifetime total and never rolls over.
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
        this.questDayStamp = today();
        this.sunCollectedToday = 0;
        this.mowerlessFirstColumnKillsToday = 0;
        this.lawnmowerKillsTotal = 0;

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

    // Per-quest completion record for the one-shot (MAIN / EPIC) quests. A completed quest has already
    // had its reward granted, so this also guards against granting the same reward twice.
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

    // Today's date as the game stamps it. One place, so the daily rollover and the stored stamp can
    // never disagree about what "today" means.
    private static String today() {
        return java.time.LocalDate.now().toString();
    }

    // Rolls the daily state forward if the stored stamp is not today's date. Called by every reader
    // and writer of daily state below, so a session that spans midnight -- or a profile loaded days
    // after it was saved -- sees a clean day without anything having to poll a clock.
    //
    // It deliberately does NOT touch the lifetime tallies (dailyQuestsDone / noneDailyQuestsDone):
    // those count everything the player has ever finished and are what the leaderboard ranks on.
    private void ensureQuestDay() {
        String today = today();
        if (today.equals(questDayStamp)) {
            return;
        }
        questDayStamp = today;
        sunCollectedToday = 0;
        mowerlessFirstColumnKillsToday = 0;
        getCompletedDailyQuestIds().clear();
        // The daily shop offer is on the same cadence and had no other reset, so it would otherwise
        // stay bought for the rest of the account's life once purchased.
        hasBoughtDailyOfferToday = false;
    }

    public Set<String> getCompletedDailyQuestIds() {
        if (completedDailyQuestIds == null) {   // a profile deserialized before this field existed
            completedDailyQuestIds = new HashSet<>();
        }
        return completedDailyQuestIds;
    }

    // Whether this daily quest has already been earned today. Tomorrow it reads false again, which is
    // what makes a daily quest repeatable.
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

    // Sun banked today, across every level played on this calendar day (Daily Sun Catcher). Reading it
    // on a new day yields 0 even if nothing has been collected yet, because the rollover runs first.
    public int getSunCollectedToday() {
        ensureQuestDay();
        return sunCollectedToday;
    }

    // Credits sun the player just picked up to today's running total, and returns the new total so a
    // caller can react to it crossing a quest threshold without a second lookup.
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

    // Lifetime lawn-mower kills (Mowing Time). No day rollover: an epic challenge is earned once, over
    // however many levels it takes.
    public int getLawnmowerKillsTotal() {
        return lawnmowerKillsTotal;
    }

    public void addLawnmowerKills(int amount) {
        if (amount > 0) {
            lawnmowerKillsTotal += amount;
        }
    }

    public void setLawnmowerKillsTotal(int lawnmowerKillsTotal) {
        this.lawnmowerKillsTotal = lawnmowerKillsTotal;
    }

    // --- Restore accessors for the daily state (used when rebuilding a profile from its record) ---

    // Raw stored value, no rollover -- the rollover happens on the first real read, so a save from an
    // earlier day correctly reads back as 0. Same contract as getRawSunCollectedToday().
    public int getRawMowerlessFirstColumnKillsToday() {
        return mowerlessFirstColumnKillsToday;
    }

    public void setMowerlessFirstColumnKillsToday(int mowerlessFirstColumnKillsToday) {
        this.mowerlessFirstColumnKillsToday = mowerlessFirstColumnKillsToday;
    }

    public String getQuestDayStamp() {
        return questDayStamp;
    }

    public void setQuestDayStamp(String questDayStamp) {
        this.questDayStamp = questDayStamp;
    }

    // Restores the raw stored value without a rollover; getSunCollectedToday() applies the rollover on
    // the first read, so a save from a previous day correctly reads back as 0.
    public int getRawSunCollectedToday() {
        return sunCollectedToday;
    }

    public void setSunCollectedToday(int sunCollectedToday) {
        this.sunCollectedToday = sunCollectedToday;
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

    // The best any single chapter has reached. A chapter-kill quest completes on whichever chapter
    // gets there first, so this is the number the travel log shows as that quest's progress.
    public int getBestChapterZombieKills() {
        int best = 0;
        for (int kills : getZombieKillsByChapter().values()) {
            best = Math.max(best, kills);
        }
        return best;
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
