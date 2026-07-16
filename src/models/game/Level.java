package models.game;

import models.game.gamemodes.GameMode;
import models.templates.LevelTemplate;

import java.util.List;

public class Level {
    private Wave[] waves;
    private LevelTemplate template;
    private boolean unlocked;
    private boolean completed;
    private GameMode gameMode;
    private int startingSun;
    private List<String> availablePlants;
    private int waveCount;
    private int seedSlots;
    private char[][] terrainLayout;

    public Level(Wave[] waves, LevelTemplate template, GameMode gameMode, int startingSun, List<String> availablePlants, int waveCount, int seedSlots, char[][] terrainLayout) {
        this.waves = waves;
        this.template = template;
        this.unlocked = false;
        this.completed = false;
        this.gameMode = gameMode;
        this.startingSun = startingSun;
        this.availablePlants = availablePlants;
        this.waveCount = waveCount;
        this.seedSlots = seedSlots;
        this.terrainLayout = terrainLayout;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public int getStartingSun() {
        return startingSun;
    }

    public LevelTemplate getTemplate() {
        return template;
    }

    public Wave[] getWaves() {
        return waves;
    }

    public List<String> getAvailablePlants() {
        return availablePlants;
    }

    public int getWaveCount() {
        return waveCount;
    }

    public int getSeedSlots() {
        return seedSlots;
    }

    public char[][] getTerrainLayout() {
        return terrainLayout;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}
