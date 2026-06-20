package models.templates;

import utils.Constants;

import java.util.List;

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
}
