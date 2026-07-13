package models.game;

import models.entities.collectibles.Sun;
import models.game.gamemodes.GameMode;
import models.map.GameMap;
import models.user.Profile;

import java.util.ArrayList;
import java.util.List;

public class GameSession {
    private Profile player;
    private GameMode mode;
    private Level level;
    private GameMap map;
    private int sunAmount;
    private List<Sun> activeSuns;
    private List<SeedPacket> selectedSeeds;
    private int plantFoodCount;
    private long timeTicks;
    private int currentWave;
    private GameState state;
    private int zombiesKilled;
    private int plantsLost;

    public GameSession(Profile player, Level level) {
        this.player = player;
        this.level = level;
        this.mode = level.getGameMode();
        this.map = new GameMap();
        this.sunAmount = level.getStartingSun();
        activeSuns = new ArrayList<>();
        selectedSeeds = new ArrayList<>();
        this.plantFoodCount = player.getPlantFoodCount();
        this.timeTicks = 0;
        currentWave = 0;
        state = GameState.PLAYING;
        zombiesKilled = 0;
        plantsLost = 0;
    }

    public List<SeedPacket> getSelectedSeeds() {
        return selectedSeeds;
    }
    public void plant(int x, int y, String plant) {};
    public void pluck(int x, int y){};
    public void advanceTime(int ticks) {};
    public void plantFood(int x, int y){};
    public void onWin(){};
    public void onLose(){};
    public Profile getPlayer() {
        return player;
    }

    public int getMaxSeedSlots() {
        if (level == null || level.getTemplate() == null) {
            return utils.Constants.DEFAULT_SEED_SLOTS;
        }
        return level.getTemplate().getSeedSlots();
    }

    public GameMode getMode() {
        return mode;
    }
    public SeedPacket getSelectedSeed(String plantType) {
        for (SeedPacket seed : selectedSeeds) {
            if (seed.getPlantType().equals(plantType)) {
                return seed;
            }
        }
        return null;
    }

    public boolean isSeedSelected(String plantType) {
        return getSelectedSeed(plantType) != null;
    }

    public Level getLevel() {
        return level;
    }

    public void addSeed(SeedPacket seed){
        selectedSeeds.add(seed);
    }

    public boolean removeSeed(String plantType){
        SeedPacket seed = getSelectedSeed(plantType);
        if(seed == null){
            return false;
        }
        selectedSeeds.remove(seed);
        return true;
    }

    public GameMap getMap() {
        return map;
    }

    public int getSunAmount() {
        return sunAmount;
    }

    public List<Sun> getActiveSuns() {
        return activeSuns;
    }

    public int getPlantFoodCount() {
        return plantFoodCount;
    }

    public long getTimeTicks() {
        return timeTicks;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public GameState getState() {
        return state;
    }

    public int getZombiesKilled() {
        return zombiesKilled;
    }

    public int getPlantsLost() {
        return plantsLost;
    }

    public void increaseSunAmount(int amount) {
        sunAmount += amount;
    }

    public void decreaseSunAmount(int amount) {sunAmount -= amount; }

    public void addSun(Sun sun) {
        activeSuns.add(sun);
    }
    public void increasePlantFoodCount(int amount) {
        plantFoodCount += amount;
        if(plantFoodCount > 3) plantFoodCount = 3;
    }
}
