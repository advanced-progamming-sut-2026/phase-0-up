package views.renderers.MenuRenderer;

import models.entities.plants.Plant;
import models.user.Profile;
import utils.registry.PlantRegistry;
import utils.registry.ZombieRegistry;
import views.OutputHandler;

public class CollectionMenuRenderer {

    public void renderAllPlants(PlantRegistry registry){}
    public void renderUnlockedPlants(Profile profile){}
    public void renderPlantDetails(Profile profile, PlantRegistry registry, String plantName){}
    public void renderAllZombies(ZombieRegistry registry){}
    public void renderSeenZombies(Profile profile){}
    public void renderZombieDetails(Profile profile, ZombieRegistry registry, String zombieName){}
    public void upgradePlant(Plant plant){}
    public void successOfPurchasePlant(){}
    public void notExist(String plantName){
        OutputHandler.showError("Plant '" + plantName +"' does not exist.");}
    public void alreadyOwned(String plantName){
        OutputHandler.showError("Plant '" + plantName + "' is already owned.");
    }
    public void notEnoughCoinToPurchase(String plantName){
        OutputHandler.showError("Not enough coins to purchase '" + plantName + "'.");
    }
    public void notOwned(String plantName){
        OutputHandler.showError("You do not own '" + plantName + "'.");
    }
    public void notEnoughSeed(String plantName){
        OutputHandler.showError("Not enough seed packets to upgrade '" + plantName + "'.");
    }
    public void notEnoughCoinToUpgrade(String plantName){
        OutputHandler.showError("Not enough coins to upgrade '" + plantName + "'.");
    }
    public void successfulUpgrade(String plantName){
        OutputHandler.showSuccess("Plant '" + plantName + "' upgraded successfully.");
    }
}
