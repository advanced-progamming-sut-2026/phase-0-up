package models.game.gamemodes;

import models.game.GameSession;

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

    // Without this the player just watches an empty sky, with nothing to say the drought is the rule
    // rather than bad luck.
    @Override
    public void onStart(GameSession gameSession) {
        super.onStart(gameSession);
        if (disableSkySun) {
            gameSession.reportEvent("Night Ops! Not a single sun will fall from this sky. Every last "
                    + "drop has to come from your own sun-producing plants -- plant them early.");
        }
    }
}
