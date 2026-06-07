package models.user;

import models.entities.plants.bowling.Plant;
import models.entities.zombies.Zombie;
import models.greenhouse.GreenHouse;
import models.quests.Quest;

import java.util.List;
import java.util.Map;

public class Profile {
    private int gameNumbers;
    private int coins;
    private int gems;
    private int bestNumberOfMeowPoints;
    private List<Plant> unlockedPlants;
    private List<Zombie> seenZombies;
    private Map<String , Integer> ownedSeedPackets;
    private GreenHouse myGreenHouse;
    private List<Quest> activeQuests;
    private List<Quest> completedQuests;
    private int lastChapter;
    private int lastLevel;
    private Map<String , Integer> passedMiniGames;
    private int dailyQuestsDone;
    private int noneDailyQuestsDone;

    public Profile(){};
    public void addCoins(int n){};
    public void spendCoins(int n){};
    public void addGems(int n){};
    public void spendGems(int n){};
    public void increaseGameNumbers(){};
    public int getGameNumbers() {return 0;}
    public int getCoins() {return 0;}
    public int getGems() {return 0;}
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
}
