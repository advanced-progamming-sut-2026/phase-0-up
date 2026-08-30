package views.renderers.MenuRenderer;

import models.game.GameSession;
import utils.registry.PlantRegistry;

// Seed selection: the almanac, the pool this level offers, and every way picking a seed can go wrong.
//
// The refusals are separate methods rather than one showError(String) on purpose -- "locked",
// "not in this level's pool" and "bolted down by Locked Plants" are three different situations, and a
// graphical build wants to disable three different things.
public interface PlantMenuRenderer {
    void renderAllPlants(PlantRegistry registry);

    void renderAvailablePlants(GameSession session);

    void plantNotSelected(String seedName);

    void alreadyBoosted(String seedName);

    void notEnoughGem();

    void successfulBoost(String seedName);

    void gameStarted();

    void notExist(String plantName);

    void isLocked(String plantName);

    // Owned, but this level does not field it -- a different refusal from isLocked.
    void notAvailableInLevel(String plantName);

    void alreadySelected(String plantName);

    void noEmptySlot();

    void successfulAdd(String plantName);

    void notSelected(String plantName);

    void successfulRemove(String plantName);

    void forcedSeedCannotAdd(String plantName);

    void forcedSeedCannotRemove(String plantName);

    // Fallback for a mode that pins a seed without marking it forced; "still locked" was plainly wrong.
    void seedPinnedByMode(String plantName);

    // ---- the Imitater ---------------------------------------------------------------------------
    //
    // Picking it is two choices, not one: the Imitater, then the plant it copies. A graphical build
    // makes that a second click; a typed one makes it a second word.

    // Asked for on its own, or asked to copy itself. Either way it still needs a plant to be.
    void imitaterNeedsTarget();

    // Already spent on the named plant -- there is only one Imitater.
    void imitaterAlreadyUsed(String copying);

    void successfulImitate(String plantName);
}
