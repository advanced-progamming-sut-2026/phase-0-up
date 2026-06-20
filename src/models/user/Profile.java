package models.user;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.Chapter;
import models.greenhouse.GreenHouse;
import models.news.News;
import models.quests.Quest;
import utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
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
    private List<Plant> unlockedPlants;
    private List<Plant> lockedPlants;
    private List<Zombie> seenZombies;
    private Map<String , Integer> ownedSeedPackets;
    private GreenHouse myGreenHouse;
    private List<Quest> activeQuests;
    private List<Quest> completedQuests;
    private int lastChapter;
    private int lastLevel;
    private List<Chapter> unlockedChapters;
    private Chapter currentChapter;
    private Map<String , Integer> passedMiniGames;
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
        this.seenZombies = new ArrayList<>();
        this.activeQuests = new ArrayList<>();
        this.completedQuests = new ArrayList<>();

        this.ownedSeedPackets = new HashMap<>();
        this.passedMiniGames = new HashMap<>();

        this.myGreenHouse = new GreenHouse();

        initializeStartingPlants();
    }


    public Chapter getCurrentChapter() {return currentChapter;}
    public void setCurrentChapter(Chapter currentChapter) {this.currentChapter = currentChapter;}
    public void addUnlockedChapter(Chapter chapter) {unlockedChapters.add(chapter);}
    public List<Chapter> getUnlockedChapters() {return unlockedChapters;}
    public void addCoins(int n){coins += n;}
    public void spendCoins(int n){coins -= n;}
    public void addGems(int n){gems += n;}
    public void spendGems(int n){gems -= n;}
    public void increaseGameNumbers(){gameNumbers++;}
    public int getGameNumbers() {return gameNumbers;}
    public int getCoins() {return coins;}
    public int getGems() {return gems;}
    public Set<String> getSeenZombieAliases() {return seenZombieAliases;}
    public int getBestNumberOfMeowPoints() {return 0;}
    public List<Plant> getUnlockedPlants() {return null;}
    public List<Zombie> getSeenZombies() {return null;}
    public Map<String, Integer> getOwnedSeedPackets() {return null;}
    public GreenHouse getMyGreenHouse() {return null;}
    public List<Quest> getActiveQuests() {return null;}
    public List<Quest> getCompletedQuests() {return null;}
    public int getLastChapter() {return 0;}
    public int getLastLevel() {return 0;}
    public Map<String, Integer> getPassedMiniGames() {return null;}
    public int getDailyQuestsDone() {return 0;}
    public int getNoneDailyQuestsDone() {return 0;}
    public void setDifficultyLevel(int difficultyLevel) {this.difficultyLevel = difficultyLevel;}
    public int getDifficultyLevel() {return 0;}
    public void addNews(News news){}

    //method for initializing starting plants;
    private void initializeStartingPlants(){
        //TODO: unlock default plants for each profile
    };
}
