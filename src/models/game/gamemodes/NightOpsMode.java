package models.game.gamemodes;

// Special level: no sky-dropped suns. SunSystem.canSpawnSkySun already refuses to spawn while the
// mode is a NightOpsMode, so the flag simply confirms the level-level override (it wins over the
// chapter's EnvironmentType default). Sun must come entirely from plants.
public class NightOpsMode extends StandardMode {
    private final boolean disableSkySun;

    public NightOpsMode(boolean disableSkySun) {
        this.disableSkySun = disableSkySun;
    }

    public boolean isSkySunDisabled() {
        return disableSkySun;
    }

    @Override
    public boolean allowsSkySun() {
        return !disableSkySun;
    }
}
