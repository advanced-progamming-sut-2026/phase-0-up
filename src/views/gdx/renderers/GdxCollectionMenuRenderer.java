package views.gdx.renderers;

import models.user.Profile;
import utils.registry.PlantRegistry;
import utils.registry.ZombieRegistry;
import views.gdx.core.ToastSink;
import views.renderers.MenuRenderer.CollectionMenuRenderer;

// The almanac. CollectionScreen (T5.3-T5.5) draws the plant and zombie grids, the seed-packet progress
// and the silhouettes for zombies the player has not met, so the listing and detail methods are the
// screen's work. What remains is the outcome of buying or upgrading -- a single sentence each.
public final class GdxCollectionMenuRenderer implements CollectionMenuRenderer {

    private final ToastSink toasts;

    public GdxCollectionMenuRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void renderAllPlants(PlantRegistry registry) {
        // The plants tab is the list.
    }

    @Override
    public void renderUnlockedPlants(Profile profile) {
        // Likewise -- owned packets are drawn on the same cards.
    }

    @Override
    public void renderPlantDetails(PlantRegistry registry, String plantName) {
        // Clicking a card opens its almanac entry.
    }

    @Override
    public void renderAllZombies(ZombieRegistry registry) {
        // The zombies tab is the list.
    }

    @Override
    public void renderSeenZombies(Profile profile) {
        // Undiscovered zombies are drawn as silhouettes on that same tab.
    }

    @Override
    public void renderZombieDetails(ZombieRegistry registry, String zombieName) {
        // Clicking a card opens its almanac entry.
    }

    @Override
    public void notExist(String plantName) {
        toasts.error("No such plant as '" + plantName + "'. Check the almanac!");
    }

    @Override
    public void alreadyOwned(String plantName) {
        toasts.error("You already grow '" + plantName + "'. One is plenty!");
    }

    @Override
    public void notEnoughCoinToPurchase(String plantName) {
        toasts.error("Not enough coins for '" + plantName + "'. Time to go farming!");
    }

    @Override
    public void notOwned(String plantName) {
        toasts.error("You don't own '" + plantName + "' yet -- buy it before you upgrade it.");
    }

    @Override
    public void notEnoughSeed(String plantName) {
        toasts.error("Not enough seed packets to upgrade '" + plantName + "'. Keep collecting!");
    }

    @Override
    public void notEnoughCoinToUpgrade(String plantName) {
        toasts.error("Not enough coins to upgrade '" + plantName + "'. Sunflowers don't grow "
                + "on trees... wait.");
    }

    @Override
    public void successfulUpgrade(String plantName) {
        toasts.success("'" + plantName + "' leveled up! It's looking meaner already.");
    }

    @Override
    public void successOfPurchasePlant() {
        toasts.success("Sold! A new sprout joins your garden.");
    }

    @Override
    public void plantMaxLevel(String plantName) {
        toasts.error(String.format("'%s' is already maxed out. It cannot get any meaner.", plantName));
    }
}
