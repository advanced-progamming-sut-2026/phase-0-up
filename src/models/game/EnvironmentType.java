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

    // Maps a level's authored "chapter" string (levels.json) onto its season. Anything unknown or
    // blank falls back to Ancient Egypt, mirroring LevelInitializer's chapter grouping, so callers
    // never get a null season.
    public static EnvironmentType fromChapter(String chapter) {
        if (chapter == null || chapter.isBlank()) {
            return ANCIENT_EGYPT;
        }
        try {
            return valueOf(chapter.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ANCIENT_EGYPT;
        }
    }
}