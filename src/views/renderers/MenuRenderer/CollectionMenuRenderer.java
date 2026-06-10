package views.renderers.MenuRenderer;

import models.entities.plants.Plant;
import models.user.Profile;
import utils.registry.PlantRegistry;
import utils.registry.ZombieRegistry;

public class CollectionMenuRenderer {

    public void renderAllPlants(PlantRegistry registry){}
    public void renderUnlockedPlants(Profile profile){}
    public void renderPlantDetails(Profile profile, PlantRegistry registry, String plantName){}
    public void renderAllZombies(ZombieRegistry registry){}
    public void renderSeenZombies(Profile profile){}
    public void renderZombieDetails(Profile profile, ZombieRegistry registry, String zombieName){}
    public void upgradePlant(Plant plant){}
    public void successOfPurchasePlant(){}
}
