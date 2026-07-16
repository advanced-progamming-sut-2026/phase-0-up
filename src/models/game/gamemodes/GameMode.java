package models.game.gamemodes;

import models.game.GameSession;

import java.util.Collections;
import java.util.List;

public interface GameMode {
    void onStart(GameSession gameSession);
    void onTick(GameSession gameSession);
    boolean checkWin(GameSession gameSession);
    boolean checkLose(GameSession gameSession);
    boolean requiresSeedSelection(GameSession gameSession);
    boolean isCommandAllowed(String commandType);

    // Seed-layer hooks. The defaults leave normal levels untouched, and let a special mode bend the
    // loadout rules without GameSession needing to know which mode it holds.

    // Lets a mode shrink the usable seed slots (Locked Plants shuts slots).
    default int adjustSeedSlots(int baseSlots) {
        return baseSlots;
    }

    // Lets a mode pin a seed in place (Locked Plants' forced loadout).
    default boolean isSeedRemovable(String plantType) {
        return true;
    }

    // Seeds a mode drops into the loadout before selection begins.
    default List<String> preSelectedPlants() {
        return Collections.emptyList();
    }

    // Whether the sky may drop suns on this level. Night Ops / Plant What You Get turn it off, and a
    // level rule wins over the chapter's EnvironmentType default. SunSystem asks the mode rather than
    // testing instanceof, so the levels.json flag actually drives the behaviour.
    default boolean allowsSkySun() {
        return true;
    }
}
