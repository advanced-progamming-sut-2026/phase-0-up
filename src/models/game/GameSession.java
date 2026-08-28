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
    private int lawnmowerKills;
    private final List<String> plantedNames = new ArrayList<>();
    private final List<String> plantedCategories = new ArrayList<>();
    private long firstWaveTick = -1;
    private int killsInFirst30s;
    private boolean cooldownRemoved;
    private final List<Result> eventLog = new ArrayList<>();

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
        if (level.getTemplate() != null) {
            MapInitializer.applyFrozenZombies(this, level.getTemplate().getFrozenZombies());
        }
        map.captureBaseWaterline();   // fixes the beach's resting waterline for the tide system
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

    public List<SeedPacket> getSelectedSeeds() { return selectedSeeds; }

    public void applySeedBoosts() {
        for (SeedPacket seed : selectedSeeds) {
            seed.setBoosted(player.isSeedBoosted(seed.getPlantType()));
        }
    }
    public Result plant(int x, int y, String plantType) {
        Result offLimits = plantingRefusal(x, y);
        if (offLimits != null) {
            return offLimits;
        }
        if (mode != null && mode.managesPlantInventory()) {
            return plantFromModeInventory(x, y, plantType);
        }
        SeedPacket seed = getSelectedSeed(plantType);
        if (seed == null) {
            return new Result(false, "You didn't bring \"" + plantType + "\" to this lawn. "
                    + "Pick it during seed selection!");
        }
        if (!cooldownRemoved && !seed.isReady(timeTicks)) {
            return new Result(false, "Plant \"" + plantType + "\" is still recharging ("
                    + String.format("%.1f", seed.getRemainingCooldownSeconds(timeTicks)) + "s left).");
        }
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(plantType);
        if (template == null) {
            return new Result(false, "Never heard of a \"" + plantType + "\". Check the almanac!");
        }
        if (sunAmount < template.getCost()) {
            return new Result(false, "Not enough sun for a \"" + plantType + "\". "
                    + "Let those sunflowers cook!");
        }

        int plantLevel = player.getPlantsLevels().getOrDefault(plantType.toLowerCase().trim(), 1);
        Plant newPlant = PlantFactory.createPlant(plantType, plantLevel, x, y);
        if (newPlant == null) {
            return new Result(false, "\"" + plantType + "\" refused to sprout. Odd.");
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
            player.setSeedBoosted(plantType, false);
        }

        plantedNames.add(newPlant.getName() == null ? "" : newPlant.getName());
        plantedCategories.add(newPlant.getCategory() == null ? "" : newPlant.getCategory());

        return new Result(true, "\"" + plantType + "\" is in the ground at (" + x + ", " + y
                + "). Hold the line!");
    };

    // Why nothing may be planted on this tile, or null if something may.
    //
    // Two reasons, and they are asked as one because a caller does not care which: the tile is not on
    // the board at all, or the MODE has closed it. Versus I, Zombie is the only mode that closes any
    // -- it keeps the plant player left of the red line so the zombie player always has somewhere
    // legal to summon.
    private Result plantingRefusal(int x, int y) {
        if (!map.isValidCoordinate(x, y)) {
            return new Result(false, "There's no tile at (" + x + ", " + y + ") -- that's off the lawn.");
        }
        String refusal = mode == null ? null : mode.plantingRefusal(x, y);
        return refusal == null ? null : new Result(false, refusal);
    }

    private Result plantFromModeInventory(int x, int y, String plantType) {
        if (!mode.hasPlantAvailable(plantType)) {
            return new Result(false, "No \"" + plantType + "\" in hand. "
                    + "Crack open a vase and grab the seed packet first!");
        }
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(plantType);
        if (template == null) {
            return new Result(false, "Never heard of a \"" + plantType + "\". Check the almanac!");
        }
        int plantLevel = player.getPlantsLevels().getOrDefault(plantType.toLowerCase().trim(), 1);
        Plant newPlant = PlantFactory.createPlant(plantType, plantLevel, x, y);
        if (newPlant == null) {
            return new Result(false, "\"" + plantType + "\" refused to sprout. Odd.");
        }
        Cell cell = map.getCell(x, y);
        Result placement = cell.addPlant(newPlant);
        if (!placement.success()) {
            return placement;   // cell occupied / not plantable -- the plant stays in hand
        }
        mode.consumePlant(plantType);

        plantedNames.add(newPlant.getName() == null ? "" : newPlant.getName());
        plantedCategories.add(newPlant.getCategory() == null ? "" : newPlant.getCategory());

        return new Result(true, "\"" + plantType + "\" is in the ground at (" + x + ", " + y
                + "). Hold the line!");
    }

    public Result pluck(int x, int y){
        if (!map.isValidCoordinate(x, y)) {
            return new Result(false, "There's no tile at (" + x + ", " + y + ") -- that's off the lawn.");
        }
        Cell cell = map.getCell(x, y);
        if (cell.hasProtector()) {
            return cell.removeProtector();
        }
        // A mode may protect a plant it placed itself (Save Our Seeds); removePlant only unlinks it.
        if (cell.hasPlant() && mode != null && !mode.isPlantRemovable(x, y)) {
            return new Result(false, "The " + cell.getCurrentPlant().getName() + " at (" + x + ", " + y
                    + ") is exactly what you're defending. It stays in the ground.");
        }
        return cell.removePlant();
    };

    public Result breakVase(int x, int y) {
        if (!map.isValidCoordinate(x, y)) {
            return new Result(false, "There's no tile at (" + x + ", " + y + ") -- that's off the lawn.");
        }
        if (mode instanceof models.game.gamemodes.VaseBreakerMode) {
            return ((models.game.gamemodes.VaseBreakerMode) mode).breakVase(this, x, y);
        }
        return new Result(false, "Not a vase in sight on this lawn.");
    }

    public Result collectSeed(int x, int y) {
        if (!map.isValidCoordinate(x, y)) {
            return new Result(false, "There's no tile at (" + x + ", " + y + ") -- that's off the lawn.");
        }
        if (mode instanceof models.game.gamemodes.VaseBreakerMode) {
            return ((models.game.gamemodes.VaseBreakerMode) mode).collectSeed(this, x, y);
        }
        return new Result(false, "No seed packets lying around here.");
    }

    public Result bowlNut(String type, int x, int y) {
        if (mode instanceof models.game.gamemodes.WallnutBowlingMode) {
            return ((models.game.gamemodes.WallnutBowlingMode) mode).bowlNut(this, type, x, y);
        }
        return new Result(false, "Save the bowling for Wall-nut Bowling!");
    }

    // Dispatched on BrainLawn, not on IZombieMode: single-player I, Zombie and a versus match are both
    // played from the zombie side and both answer this command, but they are deliberately unrelated
    // classes (see BrainLawn).
    public Result summonZombie(String type, int x, int y) {
        if (mode instanceof models.game.gamemodes.BrainLawn lawn) {
            return lawn.summonZombie(this, type, x, y);
        }
        return new Result(false, "You're on the plant side here -- summoning is an I, Zombie trick.");
    }

    public Result spawnZombieCheat(String type, int x, int y) {
        if (type == null || type.isBlank()) {
            return new Result(false, "Which zombie? Give me a type to raise.");
        }
        if (!map.isValidCoordinate(x, y)) {
            return new Result(false, "There's no tile at (" + x + ", " + y + ") -- that's off the lawn.");
        }
        models.entities.zombies.Zombie zombie = factories.ZombieFactory.createZombie(type, x, y, this);
        if (zombie == null) {
            return new Result(false, "No zombie called \"" + type + "\" has ever shambled by.");
        }
        map.getRow(y).getZombies().add(zombie);
        return new Result(true, "A " + zombie.getAlias() + " claws its way up at ("
                + x + ", " + y + ").");
    }

    public Result swapPlants(int x1, int y1, int x2, int y2) {
        if (mode instanceof models.game.gamemodes.BeghouledMode) {
            return ((models.game.gamemodes.BeghouledMode) mode).swap(this, y1, x1, y2, x2);
        }
        return new Result(false, "Shuffling plants around is a Beghouled trick.");
    }

    public Result upgradePlant(String type) {
        if (mode instanceof models.game.gamemodes.BeghouledMode) {
            return ((models.game.gamemodes.BeghouledMode) mode).upgrade(this, type);
        }
        return new Result(false, "Upgrading mid-lawn is a Beghouled trick.");
    }

    // --- Domain-event queue: the Model records narrative here; nothing here calls System.out ------
    public void reportEvent(String message) {
        if (message != null && !message.isBlank()) {
            eventLog.add(new Result(true, message));
        }
    }

    // Returns and clears the events queued since the last drain.
    public List<Result> drainEvents() {
        if (eventLog.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Result> drained = new ArrayList<>(eventLog);
        eventLog.clear();
        return drained;
    }

    public void tick() { timeTicks++; }

    public void advanceTime(int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick();
        }
    }

    public void advanceWave() {
        currentWave++;
        if (currentWave == 1) {
            firstWaveTick = timeTicks;   // the clock reference for the Quick Action quest
        }
    }

    public void recordZombieKilled() {
        zombiesKilled++;
        if (firstWaveTick >= 0 && timeTicks - firstWaveTick <= 30L * utils.Constants.TICKS_PER_SECOND) {
            killsInFirst30s++;
        }
    }

    public java.util.List<String> getPlantedNames() { return plantedNames; }
    public java.util.List<String> getPlantedCategories() { return plantedCategories; }
    public int getKillsInFirst30s() { return killsInFirst30s; }

    public void recordPlantLost() { plantsLost++; }
    public int getLawnmowerKills() { return lawnmowerKills; }

    public void recordLawnmowerKills(int count) {
        if (count > 0) {
            lawnmowerKills += count;
        }
    }

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
            return new Result(false, "There's no tile at (" + x + ", " + y + ") -- that's off the lawn.");
        }
        if (plantFoodCount <= 0) {
            return new Result(false, "Your plant food jar is empty.");
        }
        Cell cell = map.getCell(x, y);
        Plant target = cell.getDefendingPlant();
        if (target == null) {
            return new Result(false, "Nothing growing at (" + x + ", " + y + ") to feed.");
        }
        target.triggerPlantFood(this);
        decreasePlantFoodCount(1);
        return new Result(true, target.getName() + " gulps down the plant food at ("
                + x + ", " + y + ") -- stand back!");
    };
    public void onWin(){
        controllers.systems.CampaignSystem.getInstance().completeLevel(player, level);
        recordMinigameCompletion();
    };
    public void onLose(){};

    // "exit game" is a loss on the same path as being overrun; ignored once the level has ended.
    public boolean forfeit() {
        if (state != GameState.PLAYING) {
            return false;
        }
        state = GameState.LOST;
        onLose();
        return true;
    }

    // wave, mini-game, cheat and ability spawns are all covered from one place.
    public void discoverZombie(String alias) {
        if (player == null || alias == null || alias.isBlank()) {
            return;
        }
        if (player.getSeenZombieAliases().add(alias)) {   // Set.add == true only on first sighting
            controllers.systems.NewsSystem.getInstance().addZombieUnlockNews(player, alias);
        }
    }

    private void recordMinigameCompletion() {
        if (player == null) {
            return;
        }
        String name = minigameName();
        if (name == null) {
            return;
        }
        int cleared = player.getPassedMiniGames().getOrDefault(name, 0);
        if (cleared >= utils.Constants.MINIGAME_LEVELS) {
            return;   // all levels of this mini-game already cleared -- nothing new to unlock
        }
        int next = cleared + 1;
        player.getPassedMiniGames().put(name, next);
        controllers.systems.NewsSystem.getInstance()
                .addMinigameUnlockNews(player, name + " level " + next);
    }

    private String minigameName() {
        if (mode instanceof models.game.gamemodes.VaseBreakerMode) {
            return "Vasebreaker";
        }
        if (mode instanceof models.game.gamemodes.IZombieMode) {
            return "I, Zombie";
        }
        if (mode instanceof models.game.gamemodes.WallnutBowlingMode) {
            return "Wall-nut Bowling";
        }
        if (mode instanceof models.game.gamemodes.BeghouledMode) {
            return "Beghouled";
        }
        return null;
    }
    public boolean isCooldownRemoved() { return cooldownRemoved; }
    public void removeCooldownRestriction() { this.cooldownRemoved = true; }
    public Profile getPlayer() { return player; }

    public int getMaxSeedSlots() {
        if (level == null || level.getTemplate() == null) {
            return utils.Constants.DEFAULT_SEED_SLOTS;
        }
        int base = level.getTemplate().getSeedSlots();
        // A mode may shut slots (Locked Plants); normal modes return the base untouched.
        return mode == null ? base : mode.adjustSeedSlots(base);
    }

    public GameMode getMode() { return mode; }
    // Ignoring case/space: a mode-pinned seed carries the level file's casing, not the player's.
    public SeedPacket getSelectedSeed(String plantType) {
        String wanted = plantType == null ? null : plantType.trim();
        for (SeedPacket seed : selectedSeeds) {
            if (wanted != null && seed.getPlantType() != null
                    && seed.getPlantType().trim().equalsIgnoreCase(wanted)) {
                return seed;
            }
        }
        return null;
    }

    public boolean isSeedSelected(String plantType) { return getSelectedSeed(plantType) != null; }
    public Level getLevel() { return level; }

    public void addSeed(SeedPacket seed){
        if (mode != null && mode.managesPlantInventory()) {
            return;
        }
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

    public GameMap getMap() { return map; }
    public int getSunAmount() { return sunAmount; }
    public List<Sun> getActiveSuns() { return activeSuns; }
    public int getPlantFoodCount() { return plantFoodCount; }
    public long getTimeTicks() { return timeTicks; }
    public int getCurrentWave() { return currentWave; }
    public GameState getState() { return state; }
    public int getZombiesKilled() { return zombiesKilled; }
    public int getPlantsLost() { return plantsLost; }
    public void increaseSunAmount(int amount) { sunAmount += amount; }
    public void decreaseSunAmount(int amount) {sunAmount -= amount; }
    public void addSun(Sun sun) { activeSuns.add(sun); }

    public void increasePlantFoodCount(int amount) {
        plantFoodCount += amount;
        if(plantFoodCount > utils.Constants.MAX_PLANT_FOOD_CAPACITY) {
            plantFoodCount = utils.Constants.MAX_PLANT_FOOD_CAPACITY;
        }
    }
    public void decreasePlantFoodCount(int amount) {
        plantFoodCount -= amount;
        if (plantFoodCount < 0) plantFoodCount = 0;
    }
}
