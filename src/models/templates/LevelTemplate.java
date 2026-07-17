package models.templates;

import utils.Constants;

import java.util.List;

// Blueprint for a single level, parsed straight from data/levels.json by Gson.
// Normal levels use only the flat fields; the optional nested "rules" object carries the
// special-level parameters (Locked Plants / Night Ops / Dead Line / Save Our Seeds) so the
// standard levels stay uncluttered.
public class LevelTemplate {
    private String id;
    private String chapter;
    private int levelNumber;
    private String mode;
    private String name;
    private int startingSun;
    private List<String> availablePlants;
    private int waveCount;
    private List<String> terrain;
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

    // Derived from the authored waves so the wave-1 budget can never drift from the wave list.
    public int getFirstWaveBudget() {
        return waves == null || waves.isEmpty() ? 0 : waves.get(0).getBudget();
    }

    public int getWaveCount() {
        return waves != null && !waves.isEmpty() ? waves.size() : waveCount;
    }

    // "terrain" is authored as one string per row (9 chars, '.' = plain ground) because a JSON grid
    // of single-character arrays is unreadable; it is widened here to the char[][] the map layer uses.
    public char[][] getTerrainLayout() {
        if (terrain == null || terrain.isEmpty()) {
            return null;
        }
        char[][] layout = new char[terrain.size()][];
        for (int i = 0; i < terrain.size(); i++) {
            layout[i] = terrain.get(i).toCharArray();
        }
        return layout;
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

    // One authored wave: which zombie aliases may spawn, the point budget the WaveSystem spends on
    // them, and the delay (seconds) before it starts.
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

    // Optional special-level parameters. Every field is inert unless the matching GameMode reads it.
    //
    // Note: "which plants may be picked" is deliberately NOT here. That is the level's availablePlants
    // pool, which every level already has and the seed menu already enforces; a Locked Plants level is
    // simply a level with a smaller pool. This only carries what a pool cannot express.
    public static class SpecialRules {
        private int lockedType;              // Locked Plants: 1 = locked slots, 2 = forced loadout
        private int lockedSlots;             // Locked Plants: seed slots shut from the start
        private List<String> forcedPlants;   // Locked Plants: pre-selected, non-removable seeds
        private boolean disableSkySun;       // Night Ops: overrides the chapter's sky-sun default
        private int deadLineColumn;          // Dead Line: X threshold no zombie may cross
        private List<PrePlacedPlant> protectedPlants; // Save Our Seeds: must-survive plants

        public int getLockedType() {
            return lockedType;
        }

        public int getLockedSlots() {
            return lockedSlots;
        }

        public List<String> getForcedPlants() {
            return forcedPlants;
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
