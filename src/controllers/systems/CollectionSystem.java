package controllers.systems;

import models.templates.PlantTemplate;
import models.user.Profile;
import utils.Constants;
import utils.registry.PlantRegistry;
import views.renderers.MenuRenderer.CollectionMenuRenderer;

import java.util.Map;

public class CollectionSystem {
    private static CollectionSystem instance;

    private CollectionSystem() {}

    public static synchronized CollectionSystem getInstance() {
        if (instance == null) {
            instance = new CollectionSystem();
        }
        return instance;
    }

    public void purchasePlant(Profile profile, String plantName){
        CollectionMenuRenderer renderer = new CollectionMenuRenderer();
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(plantName);
        if(template == null){
            renderer.notExist(plantName);
            return;
        }
        Map<String, Integer> owned = profile.getOwnedSeedPackets();
        if(owned != null && owned.getOrDefault(plantName, 0) > 0){
            renderer.alreadyOwned(plantName);
            return;
        }
        if(profile.getCoins() < Constants.NEW_PLANT_COST_COINS){
            renderer.notEnoughCoinToPurchase(plantName);
            return;
        }
        profile.spendCoins(Constants.NEW_PLANT_COST_COINS);
        owned.put(plantName , 1);
        new CollectionMenuRenderer().successOfPurchasePlant();
    }
    public void upgradePlant(Profile profile, String plantName){
        CollectionMenuRenderer renderer = new CollectionMenuRenderer();
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(plantName);
        if (template == null){
            renderer.notExist(plantName);
            return;
        }
        Map<String, Integer> owned = profile.getOwnedSeedPackets();
        int count = owned==null ? 0 : owned.getOrDefault(plantName,0);
        if(count <= 0){
            renderer.notOwned(plantName);
            return;
        }
        if(count < Constants.UPGRADE_PLANT_REQUIRED_SEED_PACKETS){
            renderer.notEnoughSeed(plantName);
            return;
        }
        if(profile.getCoins() < Constants.UPGRADE_PLANT_COST_COINS){
            renderer.notEnoughCoinToUpgrade(plantName);
            return;
        }
        profile.spendCoins(Constants.UPGRADE_PLANT_COST_COINS);
        owned.put(plantName, count-Constants.UPGRADE_PLANT_REQUIRED_SEED_PACKETS);
        renderer.successfulUpgrade(plantName);
    }
}
