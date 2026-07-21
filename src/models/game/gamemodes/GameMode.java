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

    // --- Plant-inventory hooks -------------------------------------------------------------------
    // A mode that hands the player plants itself, outside the seed-packet + sun economy (Vasebreaker,
    // whose plants come only from broken vases). When managesPlantInventory() is true, GameSession.plant
    // consults this roster instead of the selected seeds, and consumePlant removes a plant once it is
    // placed. The defaults keep every normal level on the standard seed-packet path.

    default boolean managesPlantInventory() {
        return false;
    }

    // Whether the player currently holds a plant of this type, ready to place.
    default boolean hasPlantAvailable(String plantType) {
        return false;
    }

    // Removes one plant of this type from the player's hand once it has been planted.
    default void consumePlant(String plantType) {
    }

    // The plants currently in hand (type -> count), for the "show plant status" view. Read-only.
    default java.util.Map<String, Integer> plantInventory() {
        return java.util.Collections.emptyMap();
    }
}
