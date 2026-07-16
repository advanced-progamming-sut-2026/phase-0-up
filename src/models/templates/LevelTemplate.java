package models.templates;

import utils.Constants;

import java.util.List;

// Blueprint for a single level, parsed straight from data/levels.json by Gson.
// Normal levels use only the flat fields; the optional nested "rules" object carries the
// special-level parameters (Locked Plants / Night Ops / Dead Line / Save Our Seeds) so the
// 20-odd standard levels stay uncluttered.
public class LevelTemplate {
    private String id;
    private String chapter;
    private int levelNumber;
    private String mode;
    private String name;
    private int startingSun;
    private List<String> availablePlants;
    private int firstWaveBudget;
    private int waveCount;
    private char[][] terrainLayout;
    private int seedSlots;
    private List<WaveSpec> waves;
    private SpecialRules rules;

    public String getId() {
        return id;
    }

    public String getChapter() {
        return chapter;
    }

    public int getLevelNumber() {
        return levelNumber;
    }

    public String getMode() {
        return mode;
    }

    public String getName() {
        return name;
    }

    public int getStartingSun() {
        return startingSun;
    }

    public List<String> getAvailablePlants() {
        return availablePlants;
    }

    public int getFirstWaveBudget() {
        return firstWaveBudget;
    }

    public int getWaveCount() {
        return waveCount;
    }

    public char[][] getTerrainLayout() {
        return terrainLayout;
    }

    public int getSeedSlots() {
        return seedSlots > 0 ? seedSlots : Constants.DEFAULT_SEED_SLOTS;
    }

    public List<WaveSpec> getWaves() {
        return waves;
    }

    public SpecialRules getRules() {
        return rules;
    }

    // One authored wave: which zombie aliases spawn, the point budget, and the delay (seconds)
    // before it starts. Consumed by LevelFactory to build the Wave[] the WaveSystem later drives.
    public static class WaveSpec {
        private List<String> zombies;
        private int budget;
        private int delay;
        private boolean isFinal;

        public List<String> getZombies() {
            return zombies;
        }

        public int getBudget() {
            return budget;
        }

        public int getDelay() {
            return delay;
        }

        public boolean isFinal() {
            return isFinal;
        }
    }

    // Optional special-level parameters. Every field is inert unless the matching GameMode reads it,
    // so a level only activates the rules its mode cares about.
    public static class SpecialRules {
        private List<String> bannedPlants;   // Locked Plants: plants removed from selection
        private int lockedType;              // Locked Plants: 1 = family-lock, 2 = forced loadout
        private boolean disableSkySun;       // Night Ops: overrides the chapter's sky-sun default
        private int deadLineColumn;          // Dead Line: X threshold a zombie may not cross
        private List<PrePlacedPlant> protectedPlants; // Save Our Seeds: must-survive plants

        public List<String> getBannedPlants() {
            return bannedPlants;
        }

        public int getLockedType() {
            return lockedType;
        }

        public boolean isDisableSkySun() {
            return disableSkySun;
        }

        public int getDeadLineColumn() {
            return deadLineColumn;
        }

        public List<PrePlacedPlant> getProtectedPlants() {
            return protectedPlants;
        }
    }

    // A plant the Save Our Seeds initializer pre-plants on the grid at (x, y).
    public static class PrePlacedPlant {
        private String plant;
        private int x;
        private int y;

        public String getPlant() {
            return plant;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }
}
