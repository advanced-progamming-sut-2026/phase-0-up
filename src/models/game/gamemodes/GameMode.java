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

    // Whether a mode pinned this seed into the loadout itself. A forced seed can be neither added nor
    // removed by the player: it is already on the bar and it stays there, so seed selection reports it
    // as forced instead of falling through to a generic "already selected" / "locked" message.
    default boolean isSeedForced(String plantType) {
        return false;
    }

    // Seeds a mode drops into the loadout before selection begins.
    default List<String> preSelectedPlants() {
        return Collections.emptyList();
    }

    // Whether the player may dig up the plant standing on this tile. A mode that pre-places the plants
    // the whole level is built around (Save Our Seeds) says no: Cell.removePlant only drops the board's
    // reference and never kills the plant, so digging one up would leave the mode still watching a
    // living object that can no longer be eaten -- quietly dissolving the level's lose condition.
    default boolean isPlantRemovable(int x, int y) {
        return true;
    }

    // Whether the sky may drop suns on this level. Night Ops / Plant What You Get turn it off, and a
    // level rule wins over the chapter's EnvironmentType default. SunSystem asks the mode rather than
    // testing instanceof, so the levels.json flag actually drives the behaviour.
    default boolean allowsSkySun() {
        return true;
    }

    // Whether finishing this level feeds the quest/progress system. Adventure levels and the standard
    // survival mini-games do; the "off-book" modes do not -- I, Zombie is played from the zombies'
    // side (its kills and garden mean nothing to a plant quest) and Beghouled is a match-3, so neither
    // should complete quests, break the win streak, or credit chapter kills. GameEngine asks the mode
    // rather than testing instanceof.
    default boolean countsTowardQuests() {
        return true;
    }

    // Notified by CombatSystem the moment an AI/board plant is destroyed. Normal levels ignore it; I,
    // Zombie pays the zombie player sun for breaking a plant. Called after the plant's own death effect
    // and just before it leaves the tile.
    default void onPlantDestroyed(GameSession gameSession, models.entities.plants.Plant plant) {
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
