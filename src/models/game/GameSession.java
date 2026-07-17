package models.game;

import factories.PlantFactory;
import models.entities.collectibles.Sun;
import models.entities.plants.Plant;
import models.game.gamemodes.GameMode;
import models.map.Cell;
import models.map.GameMap;
import models.templates.PlantTemplate;
import models.user.Profile;
import utils.Result;
import utils.gameinitializers.MapInitializer;
import utils.registry.PlantRegistry;

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
    private boolean cooldownRemoved;

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
        cooldownRemoved = false;

        // Terrain and any forced loadout must exist before the player reaches seed selection.
        MapInitializer.applyTerrain(this, level.getTerrainLayout());
        applyPreSelectedSeeds();
    }

    // Seeds a mode pins into the loadout up front (Locked Plants' forced-loadout variant).
    private void applyPreSelectedSeeds() {
        if (mode == null) {
            return;
        }
        for (String plantType : mode.preSelectedPlants()) {
            PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(plantType);
            if (template != null && !isSeedSelected(plantType)) {
                addSeed(new SeedPacket(plantType, (int) Math.round(template.getRecharge())));
            }
        }
    }

    public List<SeedPacket> getSelectedSeeds() {
        return selectedSeeds;
    }
    public Result plant(int x, int y, String plantType) {
        if (!map.isValidCoordinate(x, y)) {
            return new Result(false, "Invalid coordinates (" + x + ", " + y + ").");
        }
        SeedPacket seed = getSelectedSeed(plantType);
        if (seed == null) {
            return new Result(false, "Plant \"" + plantType + "\" has not been selected for this level.");
        }
        if (!cooldownRemoved && !seed.isReady(timeTicks)) {
            return new Result(false, "Plant \"" + plantType + "\" is still recharging ("
                    + String.format("%.1f", seed.getRemainingCooldownSeconds(timeTicks)) + "s left).");
        }
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(plantType);
        if (template == null) {
            return new Result(false, "Plant \"" + plantType + "\" does not exist.");
        }
        if (sunAmount < template.getCost()) {
            return new Result(false, "Not enough sun to plant \"" + plantType + "\".");
        }

        int plantLevel = player.getPlantsLevels().getOrDefault(plantType.toLowerCase().trim(), 1);
        Plant newPlant = PlantFactory.createPlant(plantType, plantLevel, x, y);
        if (newPlant == null) {
            return new Result(false, "Plant \"" + plantType + "\" could not be created.");
        }

        Cell cell = map.getCell(x, y);
        Result placement = cell.addPlant(newPlant);
        if (!placement.success()) {
            return placement;
        }

        decreaseSunAmount(template.getCost());
        if (!cooldownRemoved) {
            seed.updateLastPlantedTick(timeTicks);
        }
        if (seed.isBoosted()) {
            newPlant.triggerPlantFood(this);
            seed.setBoosted(false);
            player.setSeedBoosted(plantType, false);
        }

        return new Result(true, "Plant \"" + plantType + "\" planted at (" + x + ", " + y + ").");
    };
    public Result pluck(int x, int y){
        if (!map.isValidCoordinate(x, y)) {
            return new Result(false, "Invalid coordinates (" + x + ", " + y + ").");
        }
        Cell cell = map.getCell(x, y);
        if (cell.hasProtector()) {
            return cell.removeProtector();
        }
        return cell.removePlant();
    };
    // --- Clock -----------------------------------------------------------------------------------
    // The session owns the clock; the systems are driven by the engine, which ticks the clock once
    // per frame and then runs each system against the new value. Nothing here runs a system, so
    // advancing time from anywhere else can never double-drive them.
    public void tick() {
        timeTicks++;
    }

    public void advanceTime(int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick();
        }
    }

    // Called by the WaveSystem when a wave actually launches. currentWave is the count of waves
    // started so far, which is what StandardMode.checkWin compares against the level's wave count.
    public void advanceWave() {
        currentWave++;
    }

    // --- GameMode seam ---------------------------------------------------------------------------
    // Rule evaluation is deliberately kept OUT of advanceTime so it never overlaps with
    // TimeSystem.advance (which owns the clock). The engine calls startMode() once at level start
    // and evaluateModeRules() each tick after the systems have run.
    public void startMode() {
        if (mode != null) {
            mode.onStart(this);
        }
    }

    public void evaluateModeRules() {
        if (mode == null || state != GameState.PLAYING) {
            return;
        }
        mode.onTick(this);
        if (mode.checkLose(this)) {
            state = GameState.LOST;
            onLose();
        } else if (mode.checkWin(this)) {
            state = GameState.WON;
            onWin();
        }
    }

    public Result plantFood(int x, int y){
        if (!map.isValidCoordinate(x, y)) {
            return new Result(false, "Invalid coordinates (" + x + ", " + y + ").");
        }
        if (plantFoodCount <= 0) {
            return new Result(false, "You don't have any plant food.");
        }
        Cell cell = map.getCell(x, y);
        Plant target = cell.getDefendingPlant();
        if (target == null) {
            return new Result(false, "There is no plant at (" + x + ", " + y + ").");
        }
        target.triggerPlantFood(this);
        decreasePlantFoodCount(1);
        return new Result(true, "Fed plant food to " + target.getName() + " at (" + x + ", " + y + ").");

    };
    public void onWin(){
        controllers.systems.CampaignSystem.getInstance().completeLevel(player, level);
    };
    public void onLose(){};
    public boolean isCooldownRemoved() {
        return cooldownRemoved;
    }
    public void removeCooldownRestriction() {
        this.cooldownRemoved = true;
    }
    public Profile getPlayer() {
        return player;
    }

    public int getMaxSeedSlots() {
        if (level == null || level.getTemplate() == null) {
            return utils.Constants.DEFAULT_SEED_SLOTS;
        }
        int base = level.getTemplate().getSeedSlots();
        // A mode may shut slots (Locked Plants); normal modes return the base untouched.
        return mode == null ? base : mode.adjustSeedSlots(base);
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
    public void decreasePlantFoodCount(int amount) {
        plantFoodCount -= amount;
        if (plantFoodCount < 0) plantFoodCount = 0;
    }
}
