package models.game;

import models.entities.collectibles.Sun;
import models.game.gamemodes.GameMode;
import models.map.GameMap;
import models.user.Profile;

import java.util.List;

public class GameSession {
    private Profile player;
    private GameMode mode;
    private Level level;
    private GameMap map;
    private int sunAmount;
    private List<String> selectedPlants;
    private List<Sun> activeSuns;
    private List<SeedPacket> selectedSeeds;
    private int plantFoodCount;
    private long timeTicks;
    private int currentWave;
    private GameState state;
    private int zombiesKilled;
    private int plantsLost;

    public void plant(int x, int y, String plant) {};
    public void pluck(int x, int y){};
    public void advanceTime(int ticks) {};
    public void collectSun(int x, int y){};
    public void plantFood(int x, int y){};
    public void onWin(){};
    public void onLose(){};

}
