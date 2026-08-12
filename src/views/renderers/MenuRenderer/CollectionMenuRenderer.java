package views.renderers.MenuRenderer;

import models.user.Profile;
import utils.registry.PlantRegistry;
import utils.registry.ZombieRegistry;

// The collection almanac: every plant and zombie on file, what the player owns or has met, and the
// outcomes of buying and upgrading.
public interface CollectionMenuRenderer {
    void renderAllPlants(PlantRegistry registry);

    void renderUnlockedPlants(Profile profile);

    void renderPlantDetails(PlantRegistry registry, String plantName);

    void renderAllZombies(ZombieRegistry registry);

    void renderSeenZombies(Profile profile);

    void renderZombieDetails(ZombieRegistry registry, String zombieName);

    void notExist(String plantName);

    void alreadyOwned(String plantName);

    void notEnoughCoinToPurchase(String plantName);

    void notOwned(String plantName);

    void notEnoughSeed(String plantName);

    void notEnoughCoinToUpgrade(String plantName);

    void successfulUpgrade(String plantName);

    void successOfPurchasePlant();

    void plantMaxLevel(String plantName);
}
