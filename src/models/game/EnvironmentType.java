package models.game;

public enum EnvironmentType {
    ANCIENT_EGYPT(true, false),
    FROSTBITE_CAVES(true, false),
    BIG_WAVE_BEACH(true, true),
    DARK_AGES(false, false);

    private final boolean hasSkySunDrops;
    private final boolean hasWaterTerrain;

    EnvironmentType(boolean hasSkySunDrops, boolean hasWaterTerrain) {
        this.hasSkySunDrops = hasSkySunDrops;
        this.hasWaterTerrain = hasWaterTerrain;
    }

    public boolean hasSkySunDrops() {
        return hasSkySunDrops;
    }

    public boolean hasWaterTerrain() {
        return hasWaterTerrain;
    }
}