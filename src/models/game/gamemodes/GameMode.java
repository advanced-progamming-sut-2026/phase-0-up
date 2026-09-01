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

    // A mode-pinned seed can be neither added nor removed, and is reported as forced.
    default boolean isSeedForced(String plantType) {
        return false;
    }
    // Seeds a mode drops into the loadout before selection begins.
    default List<String> preSelectedPlants() {
        return Collections.emptyList();
    }

    // May the player dig up this tile? Save Our Seeds says no -- removePlant only drops the reference.
    default boolean isPlantRemovable(int x, int y) {
        return true;
    }

    // Why this tile may not be planted on, or null if it may. The sibling of isPlantRemovable, and a
    // String rather than a boolean because the answer is shown to the player: "you can't plant there"
    // with no reason is the kind of refusal that reads as a bug. Versus I, Zombie is the only user --
    // it keeps the plant player left of the red line, so the zombie player always has somewhere legal
    // to summon.
    default String plantingRefusal(int x, int y) {
        return null;
    }
    // Whether the sky may drop suns on this level. Night Ops / Plant What You Get turn it off, and a
    // level rule wins over the chapter's EnvironmentType default. SunSystem asks the mode rather than
    // testing instanceof, so the levels.json flag actually drives the behaviour.
    default boolean allowsSkySun() {
        return true;
    }

    // What this level is asking of the player, in the mode's own words, or null to let the standard
    // "survive N waves" stand.
    //
    // A mode with a goal of its own has always needed this -- the objective card counts WAVES, and a
    // mode that has none falls through to "Hold the lawn.", which is true of every level in the game and
    // says nothing about this one. A boss level is the case that made it necessary: it authors no waves
    // at all, and what it wants is a specific machine brought down.
    default String describeObjective(GameSession gameSession) {
        return null;
    }

    // Does finishing this level feed quests/progress? I, Zombie and Beghouled do not.
    default boolean countsTowardQuests() {
        return true;
    }
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

    // Why the player cannot plant this right now, when the mode is the one handing plants out.
    //
    // A mode that manages its own inventory also owns the REASON one is missing, and the two modes that
    // do are not alike: a Vasebreaker plant comes out of a vase nobody has smashed yet, a boss level's
    // comes up a belt that has not delivered it. This wording used to be Vasebreaker's, hard-coded in
    // GameSession -- so a boss level told the player to go and crack open a vase on a lawn that has
    // none, which reads as the game being confused about which mode it is in.
    default String plantUnavailableMessage(String plantType) {
        return "No \"" + plantType + "\" in hand right now.";
    }

    // The plants currently in hand (type -> count), for the "show plant status" view. Read-only.
    default java.util.Map<String, Integer> plantInventory() {
        return java.util.Collections.emptyMap();
    }
}
