package models.quests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// A snapshot of the facts a quest condition is judged against, captured when a level ends. Built by
// the QuestSystem from the finished GameSession; conditions only read it. Grows by builder as new
// tracked facts are added, so no call site has to pass a dozen positional arguments.
public class QuestContext {
    private final boolean won;
    private final int sunCollected;      // total sun the player banked over the level
    private final int finalSun;          // sun left in the bank at level end
    private final int zombiesKilled;
    private final int plantsLost;
    private final int lawnmowerKills;
    private final int killsInFirst30s;   // kills within 30s of the first wave (Quick Action)
    private final int mowerlessFirstColumnKills;  // kills in col 0 of a mower-spent row (Almost Victorious)
    private final int winStreakAtMaxDifficulty;   // consecutive max-difficulty wins incl. this level (Win After Win)
    private final int chapterZombiesKilled;       // total kills in this level's chapter so far (Chapter Hunter)
    private final Map<String, Integer> killsByPlant;   // lower-cased plant name -> kills credited to it
    private final Map<String, Integer> killsByFamily;  // lower-cased plant family (category) -> kills credited
    private final List<String> plantedCategories;      // category of every plant placed this level
    private final List<String> plantedNames;           // name of every plant placed this level
    private final boolean[][] plantGrid;  // [row][col] -> a plant stood there at level end

    private QuestContext(Builder b) {
        this.won = b.won;
        this.sunCollected = b.sunCollected;
        this.finalSun = b.finalSun;
        this.zombiesKilled = b.zombiesKilled;
        this.plantsLost = b.plantsLost;
        this.lawnmowerKills = b.lawnmowerKills;
        this.killsInFirst30s = b.killsInFirst30s;
        this.mowerlessFirstColumnKills = b.mowerlessFirstColumnKills;
        this.winStreakAtMaxDifficulty = b.winStreakAtMaxDifficulty;
        this.chapterZombiesKilled = b.chapterZombiesKilled;
        this.killsByPlant = b.killsByPlant == null ? new HashMap<>() : b.killsByPlant;
        this.killsByFamily = b.killsByFamily == null ? new HashMap<>() : b.killsByFamily;
        this.plantedCategories = b.plantedCategories == null ? new ArrayList<>() : b.plantedCategories;
        this.plantedNames = b.plantedNames == null ? new ArrayList<>() : b.plantedNames;
        this.plantGrid = b.plantGrid;
    }

    public static Builder builder() {
        return new Builder();
    }

    // --- Legacy positional constructors (used by focused tests) ----------------------------------
    public QuestContext(boolean won, int sunCollected, int finalSun, int zombiesKilled,
                        int plantsLost, int lawnmowerKills, Map<String, Integer> killsByPlant,
                        boolean[][] plantGrid) {
        this(builder().won(won).sunCollected(sunCollected).finalSun(finalSun).zombiesKilled(zombiesKilled)
                .plantsLost(plantsLost).lawnmowerKills(lawnmowerKills).killsByPlant(killsByPlant)
                .plantGrid(plantGrid));
    }

    public QuestContext(boolean won, int sunCollected, int finalSun, int zombiesKilled,
                        int plantsLost, int lawnmowerKills, boolean[][] plantGrid) {
        this(won, sunCollected, finalSun, zombiesKilled, plantsLost, lawnmowerKills, null, plantGrid);
    }

    public static class Builder {
        private boolean won;
        private int sunCollected, finalSun, zombiesKilled, plantsLost, lawnmowerKills, killsInFirst30s;
        private int mowerlessFirstColumnKills, winStreakAtMaxDifficulty, chapterZombiesKilled;
        private Map<String, Integer> killsByPlant, killsByFamily;
        private List<String> plantedCategories, plantedNames;
        private boolean[][] plantGrid;

        public Builder won(boolean v) { this.won = v; return this; }
        public Builder sunCollected(int v) { this.sunCollected = v; return this; }
        public Builder finalSun(int v) { this.finalSun = v; return this; }
        public Builder zombiesKilled(int v) { this.zombiesKilled = v; return this; }
        public Builder plantsLost(int v) { this.plantsLost = v; return this; }
        public Builder lawnmowerKills(int v) { this.lawnmowerKills = v; return this; }
        public Builder killsInFirst30s(int v) { this.killsInFirst30s = v; return this; }
        public Builder mowerlessFirstColumnKills(int v) { this.mowerlessFirstColumnKills = v; return this; }
        public Builder winStreakAtMaxDifficulty(int v) { this.winStreakAtMaxDifficulty = v; return this; }
        public Builder chapterZombiesKilled(int v) { this.chapterZombiesKilled = v; return this; }
        public Builder killsByPlant(Map<String, Integer> v) { this.killsByPlant = v; return this; }
        public Builder killsByFamily(Map<String, Integer> v) { this.killsByFamily = v; return this; }
        public Builder plantedCategories(List<String> v) { this.plantedCategories = v; return this; }
        public Builder plantedNames(List<String> v) { this.plantedNames = v; return this; }
        public Builder plantGrid(boolean[][] v) { this.plantGrid = v; return this; }
        public QuestContext build() { return new QuestContext(this); }
    }

    public boolean isWon() { return won; }
    public int getSunCollected() { return sunCollected; }
    public int getFinalSun() { return finalSun; }
    public int getZombiesKilled() { return zombiesKilled; }
    public int getPlantsLost() { return plantsLost; }
    public int getLawnmowerKills() { return lawnmowerKills; }

    public Map<String, Integer> getKillsByPlant() { return Collections.unmodifiableMap(killsByPlant); }

    // Kills credited to one plant type (by name, case-insensitive).
    public int killsByPlant(String plantName) {
        return plantName == null ? 0 : killsByPlant.getOrDefault(plantName.toLowerCase().trim(), 0);
    }

    // How many distinct plant types were credited with a kill.
    public int distinctKillerPlants() {
        return killsByPlant.size();
    }

    public Map<String, Integer> getKillsByFamily() { return Collections.unmodifiableMap(killsByFamily); }

    // How many distinct plant families (categories) were credited with a kill (Family Massacre).
    public int distinctKillerFamilies() {
        return killsByFamily.size();
    }

    public int getKillsInFirst30s() { return killsInFirst30s; }

    // Kills landed in column 0 of a row whose lawn mower was already spent (Almost Victorious).
    public int getMowerlessFirstColumnKills() { return mowerlessFirstColumnKills; }

    // Running streak of consecutive wins at maximum difficulty, this level included (Win After Win).
    public int getWinStreakAtMaxDifficulty() { return winStreakAtMaxDifficulty; }

    // Total zombies felled in this level's chapter so far, this level included (Chapter Hunter).
    public int getChapterZombiesKilled() { return chapterZombiesKilled; }

    // --- Plantings placed over the level (cumulative, not just what survived) ---------------------
    public int plantedCount() { return plantedNames.size(); }

    // How many placed plants had this category (e.g. "EXPLOSIVE").
    public int plantedCategoryCount(String category) {
        if (category == null) {
            return 0;
        }
        int n = 0;
        for (String c : plantedCategories) {
            if (category.equalsIgnoreCase(c)) {
                n++;
            }
        }
        return n;
    }

    // Whether every plant placed was of one category, and at least one was placed.
    public boolean allPlantedAreCategory(String category) {
        if (plantedCategories.isEmpty()) {
            return false;
        }
        for (String c : plantedCategories) {
            if (!category.equalsIgnoreCase(c)) {
                return false;
            }
        }
        return true;
    }

    // Whether every plant placed was a mushroom (its name ends in "-shroom"), and at least one was.
    public boolean allPlantedAreMushrooms() {
        if (plantedNames.isEmpty()) {
            return false;
        }
        for (String name : plantedNames) {
            if (name == null || !name.toLowerCase().contains("shroom")) {
                return false;
            }
        }
        return true;
    }

    // Whether the given column and row are both empty (Defenseless Cross).
    public boolean isCrossEmpty(int index) {
        return isColumnEmpty(index) && isRowEmpty(index);
    }

    public int getRows() { return plantGrid == null ? 0 : plantGrid.length; }
    public int getCols() { return plantGrid == null || plantGrid.length == 0 ? 0 : plantGrid[0].length; }

    public boolean hasPlantAt(int row, int col) {
        return plantGrid != null && row >= 0 && row < getRows()
                && col >= 0 && col < getCols() && plantGrid[row][col];
    }

    // Whether a whole row is free of plants.
    public boolean isRowEmpty(int row) {
        if (row < 0 || row >= getRows()) {
            return false;
        }
        for (int c = 0; c < getCols(); c++) {
            if (plantGrid[row][c]) {
                return false;
            }
        }
        return true;
    }

    // Whether a whole column is free of plants.
    public boolean isColumnEmpty(int col) {
        if (col < 0 || col >= getCols()) {
            return false;
        }
        for (int r = 0; r < getRows(); r++) {
            if (plantGrid[r][col]) {
                return false;
            }
        }
        return true;
    }

    // Whether the garden is mirror-symmetric top-to-bottom (row r matches row rows-1-r).
    public boolean isVerticallySymmetric() {
        int rows = getRows();
        for (int r = 0; r < rows / 2; r++) {
            for (int c = 0; c < getCols(); c++) {
                if (plantGrid[r][c] != plantGrid[rows - 1 - r][c]) {
                    return false;
                }
            }
        }
        return true;
    }
}
