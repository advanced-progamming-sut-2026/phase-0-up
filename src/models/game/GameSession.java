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

    public List<SeedPacket> getSelectedSeeds() {
        return selectedSeeds;
    }

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
}
