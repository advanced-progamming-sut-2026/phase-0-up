package models.game.gamemodes;

// Maps the SCREAMING_SNAKE "mode" string in levels.json onto a concrete GameMode strategy.
// LevelFactory switches on this to instantiate the right mode with its rule data.
public enum GameModeType {
    STANDARD,
    CONVEYOR_BELT,
    LOCKED_PLANTS,
    NIGHT_OPS,
    DEAD_LINE,
    SAVE_OUR_SEEDS,
    LOVE_YOUR_PLANTS,
    TIMED_WAR,
    PLANT_WHAT_YOU_GET;

    public static GameModeType fromJson(String raw) {
        if (raw == null) {
            return STANDARD;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return STANDARD;
        }
    }
}
