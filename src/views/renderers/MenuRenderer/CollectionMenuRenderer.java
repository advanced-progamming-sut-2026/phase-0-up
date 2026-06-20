package views.renderers.MenuRenderer;

import models.entities.plants.Plant;
import models.templates.PlantTemplate;
import models.templates.ZombieTemplate;
import models.user.Profile;
import utils.registry.PlantRegistry;
import utils.registry.ZombieRegistry;
import views.OutputHandler;

import java.util.Map;
import java.util.Set;

public class CollectionMenuRenderer {

    private String formatPlant(PlantTemplate template) {
        return template.getName()
                + " | cost: " + template.getCost()
                + " | recharge: " + template.getRecharge();
    }

    private String formatZombie(ZombieTemplate template) {
        return template.getAlias()
                + " | hp: " + template.getBaseHp()
                + " | armor: " + template.getArmorHp()
                + " | speed: " + template.getSpeed();
    }

    public void renderAllPlants(PlantRegistry registry){
        Map<String, PlantTemplate> all = registry.getAllPlantTemplates();
        if (all == null || all.isEmpty()) {
            OutputHandler.showMessage("No plants are defined in the game.");
            return;
        }
        OutputHandler.showMessage("All plants:");
        for (PlantTemplate template : all.values()) {
            OutputHandler.showMessage(formatPlant(template));
        }
    }
    public void renderUnlockedPlants(Profile profile){
        Map<String, Integer> owned = profile.getOwnedSeedPackets();
        if (owned == null || owned.isEmpty()) {
            OutputHandler.showMessage("You have not acquired any plants yet.");
            return;
        }
        OutputHandler.showMessage("Acquired plants:");
        PlantRegistry registry = PlantRegistry.getInstance();
        for (Map.Entry<String, Integer> entry : owned.entrySet()) {
            PlantTemplate template = registry.getTemplateByName(entry.getKey());
            String line = template == null ? entry.getKey() : formatPlant(template);
            OutputHandler.showMessage(line + " | owned: " + entry.getValue());
        }
    }
    public void renderPlantDetails(PlantRegistry registry, String plantName){
        PlantTemplate template = registry.getTemplateByName(plantName);
        if (template == null) {
            OutputHandler.showError("Plant '" + plantName + "' does not exist.");
            return;
        }
        OutputHandler.showMessage("Plant details:");
        OutputHandler.showMessage("name: " + template.getName());
        OutputHandler.showMessage("category: " + template.getCategory());
        OutputHandler.showMessage("cost: " + template.getCost());
        OutputHandler.showMessage("recharge: " + template.getRecharge());
        OutputHandler.showMessage("base hp: " + template.getBaseHp());
        OutputHandler.showMessage("sun production rate: " + template.getSunProductionRate());
        OutputHandler.showMessage("base ability: " + template.getBaseAbility());
        OutputHandler.showMessage("plant food effect: " + template.getPlantFoodEffect());
    }
    public void renderAllZombies(ZombieRegistry registry){
        Map<String, ZombieTemplate> all = registry.getZombieTemplatesByAlias();
        OutputHandler.showMessage("All zombies:");
        if (all == null || all.isEmpty()) {
            OutputHandler.showMessage("No zombies are defined in the game.");
            return;
        }
        for (ZombieTemplate template : all.values()) {
            OutputHandler.showMessage(formatZombie(template));
        }
    }
    public void renderSeenZombies(Profile profile){
        Set<String> seen = profile.getSeenZombieAliases();
        OutputHandler.showMessage("Seen zombies:");
        if (seen == null || seen.isEmpty()) {
            OutputHandler.showMessage("You have not seen any zombies yet.");
            return;
        }
        ZombieRegistry registry = ZombieRegistry.getInstance();
        for (String alias : seen) {
            ZombieTemplate template = registry.getZombieTemplateByAlias(alias);
            OutputHandler.showMessage(template == null ? alias : formatZombie(template));
        }
    }
    public void renderZombieDetails(ZombieRegistry registry, String zombieName){
        ZombieTemplate template = registry.getZombieTemplateByAlias(zombieName);
        if (template == null) {
            OutputHandler.showError("Zombie '" + zombieName + "' does not exist.");
            return;
        }
        OutputHandler.showMessage("Zombie details:");
        OutputHandler.showMessage("alias: " + template.getAlias());
        OutputHandler.showMessage("category: " + template.getCategory());
        OutputHandler.showMessage("base hp: " + template.getBaseHp());
        OutputHandler.showMessage("armor hp: " + template.getArmorHp());
        OutputHandler.showMessage("eat damage: " + template.getEatDamage());
        OutputHandler.showMessage("eat speed: " + template.getEatSpeed());
        OutputHandler.showMessage("speed: " + template.getSpeed());
        OutputHandler.showMessage("behavior: " + template.getBehavior());
    }
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
    public void successOfPurchasePlant(){
        OutputHandler.showSuccess("Plant purchased successfully.");
    }

}
