package factories;

import models.game.Level;
import models.game.Wave;
import models.game.gamemodes.DeadLineMode;
import models.game.gamemodes.GameMode;
import models.game.gamemodes.GameModeType;
import models.game.gamemodes.LockedPlantsMode;
import models.game.gamemodes.NightOpsMode;
import models.game.gamemodes.SaveOurSeedsMode;
import models.game.gamemodes.StandardMode;
import models.game.gamemodes.BeghouledMode;
import models.game.gamemodes.IZombieMode;
import models.game.gamemodes.VaseBreakerMode;
import models.game.gamemodes.WallnutBowlingMode;
import models.templates.LevelTemplate;
import models.templates.LevelTemplate.SpecialRules;
import models.templates.LevelTemplate.WaveSpec;
import utils.registry.LevelRegistry;

import java.util.ArrayList;
import java.util.List;

// Builds a live Level from a registered blueprint: selects the GameMode strategy and hands it its
// rule data, assembles the Wave[], and computes the final selectable-plant list. This is the single
// place that wires special-level parameters into a mode, so GameSession and the core loop never
// branch on level type.
public final class LevelFactory {
    private LevelFactory() { }

    public static Level createLevel(String id) {
        LevelTemplate template = LevelRegistry.getInstance().getLevelTemplateById(id);
        if (template == null) {
            return null;
        }

        GameMode mode = buildGameMode(template);
        Wave[] waves = buildWaves(template);
        List<String> availablePlants = resolveAvailablePlants(template);

        return new Level(waves, template, mode, template.getStartingSun(), availablePlants,
                waves.length, template.getSeedSlots(), template.getTerrainLayout());
    }

    private static GameMode buildGameMode(LevelTemplate template) {
        SpecialRules rules = template.getRules();
        switch (GameModeType.fromJson(template.getMode())) {
            case LOCKED_PLANTS:
                return new LockedPlantsMode(
                        rules != null ? rules.getLockedType() : 1,
                        rules != null ? rules.getLockedSlots() : 0,
                        rules != null ? rules.getForcedPlants() : null);
            case NIGHT_OPS:
                return new NightOpsMode(rules == null || rules.isDisableSkySun());
            case DEAD_LINE:
                return new DeadLineMode(rules != null ? rules.getDeadLineColumn() : 0);
            case SAVE_OUR_SEEDS:
                return new SaveOurSeedsMode(rules != null ? rules.getProtectedPlants() : null);
            case VASE_BREAKER:
                return new VaseBreakerMode(rules != null && rules.getDifficulty() > 0 ? rules.getDifficulty() : 1);
            case WALL_NUT_BOWLING:
                return new WallnutBowlingMode(rules != null && rules.getDifficulty() > 0 ? rules.getDifficulty() : 1);
            case I_ZOMBIE:
                return new IZombieMode(rules != null && rules.getDifficulty() > 0 ? rules.getDifficulty() : 1);
            case BEGHOULED:
                return new BeghouledMode(rules != null && rules.getDifficulty() > 0 ? rules.getDifficulty() : 1);
            case STANDARD:
            default:
                return new StandardMode();
        }
    }

    private static Wave[] buildWaves(LevelTemplate template) {
        List<WaveSpec> specs = template.getWaves();
        if (specs == null || specs.isEmpty()) {
            // Fall back to an empty wave sequence sized by waveCount; the WaveSystem can still drive it.
            int count = Math.max(0, template.getWaveCount());
            Wave[] waves = new Wave[count];
            for (int i = 0; i < count; i++) {
                waves[i] = new Wave(i + 1, i == count - 1, 0, 0, new ArrayList<>());
            }
            return waves;
        }

        Wave[] waves = new Wave[specs.size()];
        for (int i = 0; i < specs.size(); i++) {
            WaveSpec spec = specs.get(i);
            boolean last = spec.isFinal() || i == specs.size() - 1;
            waves[i] = new Wave(i + 1, last, spec.getBudget(), spec.getDelay(), spec.getZombies());
        }
        return waves;
    }

    // The level's plant pool -- the single source of truth for what may be selected. A "Locked Plants"
    // level simply ships a smaller pool, so no separate ban list is needed.
    private static List<String> resolveAvailablePlants(LevelTemplate template) {
        List<String> available = new ArrayList<>();
        if (template.getAvailablePlants() != null) {
            available.addAll(template.getAvailablePlants());
        }
        return available;
    }
}
