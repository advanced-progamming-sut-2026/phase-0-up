package views.gdx.renderers;

import models.game.GameSession;
import utils.registry.PlantRegistry;
import views.gdx.core.ToastSink;
import views.renderers.MenuRenderer.PlantMenuRenderer;

// Seed selection.
//
// SeedSelectionScreen (T4.7) draws the almanac and the loadout as cards, so the two listing methods
// have nothing to say. Every refusal still does: the player has just clicked a card and needs to know
// why it did not go on the bar, and the reasons are already distinct methods rather than one string.
public final class GdxPlantMenuRenderer implements PlantMenuRenderer {

    private final ToastSink toasts;

    public GdxPlantMenuRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void renderAllPlants(PlantRegistry registry) {
        // The card grid is the almanac.
    }

    @Override
    public void renderAvailablePlants(GameSession session) {
        // The card grid is the loadout.
    }

    @Override
    public void plantNotSelected(String seedName) {
        toasts.error("'" + seedName + "' isn't in your loadout, so there's nothing to do to it.");
    }

    @Override
    public void alreadyBoosted(String seedName) {
        toasts.error("'" + seedName + "' is already fizzing with plant food. Save your gems!");
    }

    @Override
    public void notEnoughGem() {
        toasts.error("Not enough gems for a boost. Those things are precious.");
    }

    @Override
    public void successfulBoost(String seedName) {
        toasts.success("'" + seedName + "' is supercharged -- it'll fire off its plant food "
                + "the moment you plant it!");
    }

    @Override
    public void gameStarted() {
        toasts.success("The lawn is set. Here they come -- good luck out there!");
    }

    @Override
    public void notExist(String plantName) {
        toasts.error("No such plant as '" + plantName + "'. Check the almanac!");
    }

    @Override
    public void isLocked(String plantName) {
        toasts.error("'" + plantName + "' is still locked. Unlock it in the collection first.");
    }

    @Override
    public void notAvailableInLevel(String plantName) {
        toasts.error("'" + plantName + "' isn't one of the plants this level offers.");
    }

    @Override
    public void alreadySelected(String plantName) {
        toasts.error("'" + plantName + "' is already on the seed bar.");
    }

    @Override
    public void noEmptySlot() {
        toasts.error("Seed bar is full! Drop something before you pick up another.");
    }

    @Override
    public void successfulAdd(String plantName) {
        toasts.success("'" + plantName + "' loaded onto the seed bar.");
    }

    @Override
    public void notSelected(String plantName) {
        toasts.error("'" + plantName + "' isn't on the seed bar.");
    }

    @Override
    public void successfulRemove(String plantName) {
        toasts.success("'" + plantName + "' taken off the seed bar.");
    }

    @Override
    public void forcedSeedCannotAdd(String plantName) {
        toasts.error("'" + plantName + "' is bolted to your seed bar by Locked Plants -- "
                + "it's already loaded.");
    }

    @Override
    public void forcedSeedCannotRemove(String plantName) {
        toasts.error("'" + plantName + "' is bolted to your seed bar by Locked Plants -- "
                + "this lawn insists you bring it.");
    }

    @Override
    public void seedPinnedByMode(String plantName) {
        toasts.error("'" + plantName + "' is pinned to your seed bar by this level's rules.");
    }

    @Override
    public void imitaterNeedsTarget() {
        toasts.error("Pick the plant the Imitater should copy.");
    }

    @Override
    public void imitaterAlreadyUsed(String copying) {
        toasts.error("Your Imitater is already dressed up as '" + copying + "'.");
    }

    @Override
    public void successfulImitate(String plantName) {
        toasts.success("The Imitater squeezes into a '" + plantName + "' costume.");
    }
}
