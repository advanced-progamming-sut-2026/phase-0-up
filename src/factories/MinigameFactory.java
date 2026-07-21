package factories;

import factories.zombie.ZombotanyRoster;
import models.game.Level;
import models.game.Wave;
import models.game.gamemodes.BeghouledMode;
import models.game.gamemodes.IZombieMode;
import models.game.gamemodes.StandardMode;
import models.game.gamemodes.ScoringMode;
import models.game.gamemodes.VaseBreakerMode;
import models.templates.PlantTemplate;
import utils.Constants;
import utils.registry.PlantRegistry;

import java.util.Collections;
import models.game.gamemodes.WallnutBowlingMode;
import utils.Constants;

import java.util.ArrayList;
import java.util.List;

// Builds a self-contained Level for a mini-game launched from the Travel Log. Mini-games are not part
// of the adventure map, so they carry no LevelTemplate, no waves, and no selectable-plant pool -- the
// mode generates everything it needs in onStart. The board is the standard 5x9 grid the GameMap always
// builds, so a null terrain layout is fine.
public final class MinigameFactory {
    private MinigameFactory() { }

    // The scoring game (بازی امتیازی): an adventure-style level whose waves are generated from the
    // day's seed, so everyone playing on this date meets the same assault and their Meow Points compare.
    // Unlike the other mini-games this one DOES use seed selection and sun, because it plays like a
    // normal level -- only the scoring is special.
    public static Level createScoringGame() {
        ScoringMode mode = new ScoringMode();
        Wave[] waves = DailyWaveGenerator.generate(mode.getSeed());
        return new Level(waves, null, mode, Constants.SCORING_GAME_STARTING_SUN,
                scoringPlantPool(), waves.length, Constants.DEFAULT_SEED_SLOTS, null);
    }

    // Everything the player may bring to a scoring run. Deliberately the whole catalogue rather than a
    // fixed hand -- the mode is about out-scoring other people with your own strategy, so the loadout
    // is the strategy. Seed selection intersects this with what the player actually owns, so the pool
    // being broad never hands anyone a plant they have not unlocked.
    //
    // Water plants and lily pads are left out: this lawn is dry, so they could never be placed.
    private static List<String> scoringPlantPool() {
        List<String> pool = new ArrayList<>();
        for (PlantTemplate t : PlantRegistry.getInstance().getAllPlantTemplates().values()) {
            if (t.getName() == null || t.getName().isBlank() || t.isPlatform()) {
                continue;
            }
            List<String> tags = t.getTags();
            if (tags != null && tags.contains("WATER")) {
                continue;
            }
            pool.add(t.getName());
        }
        Collections.sort(pool);   // stable order, so the menu reads the same for everyone
        return pool;
    }

    public static Level createVasebreaker(int difficulty) {
        VaseBreakerMode mode = new VaseBreakerMode(Math.max(1, difficulty));
        return new Level(new Wave[0], null, mode, 0, new ArrayList<>(), 0, 0, null);
    }

    public static Level createWallnutBowling(int difficulty) {
        WallnutBowlingMode mode = new WallnutBowlingMode(Math.max(1, difficulty));
        return new Level(new Wave[0], null, mode, 0, new ArrayList<>(), 0, 0, null);
    }

    public static Level createIZombie(int difficulty) {
        IZombieMode mode = new IZombieMode(Math.max(1, difficulty));
        // The mode itself sets the 150-sun starting bank in onStart.
        return new Level(new Wave[0], null, mode, 0, new ArrayList<>(), 0, 0, null);
    }

    public static Level createBeghouled(int difficulty) {
        BeghouledMode mode = new BeghouledMode(Math.max(1, difficulty));
        return new Level(new Wave[0], null, mode, 0, new ArrayList<>(), 0, 0, null);
    }

    // Zombotany is an otherwise-normal level (plant selection, sun, win/lose all standard) whose waves
    // are the plant-zombies. The four blueprints are registered up front so the waves can spawn them.
    public static Level createZombotany(int difficulty) {
        ZombotanyRoster.register();
        int d = Math.max(1, difficulty);
        List<String> availablePlants = new ArrayList<>(List.of(
                "Peashooter", "Sunflower", "Wall-nut", "Cherry Bomb", "Potato Mine", "Jalapeno"));
        Wave[] waves = buildZombotanyWaves(d);
        return new Level(waves, null, new StandardMode(), 50, availablePlants, waves.length, 6, null);
    }

    private static Wave[] buildZombotanyWaves(int difficulty) {
        List<String> pool = List.of(ZombotanyRoster.PEASHOOTER, ZombotanyRoster.WALLNUT,
                ZombotanyRoster.JALAPENO, ZombotanyRoster.SQUASH, "ZombieDefault");
        int waveCount = 3 + difficulty;
        Wave[] waves = new Wave[waveCount];
        for (int i = 0; i < waveCount; i++) {
            boolean last = i == waveCount - 1;
            int budget = (5 + i * 3) * difficulty;
            int delay = 8 * Constants.TICKS_PER_SECOND;
            waves[i] = new Wave(i + 1, last, budget, delay, new ArrayList<>(pool));
        }
        return waves;
    }
}
