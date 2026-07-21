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
                + " | armor: " + template.getArmors()
                + " | speed: " + template.getSpeed();
    }

    public void renderAllPlants(PlantRegistry registry){
        Map<String, PlantTemplate> all = registry.getAllPlantTemplates();
        if (all == null || all.isEmpty()) {
            OutputHandler.showMessage("The almanac is empty. Somebody forgot to water the database.");
            return;
        }
        OutputHandler.showMessage("--- The Almanac: every plant known to science ---");
        for (PlantTemplate template : all.values()) {
            OutputHandler.showMessage(formatPlant(template));
        }
    }
    public void renderUnlockedPlants(Profile profile){
        Map<String, Integer> owned = profile.getOwnedSeedPackets();
        if (owned == null || owned.isEmpty()) {
            OutputHandler.showMessage("Your seed tray is bare. Pop into the shop and start a collection!");
            return;
        }
        OutputHandler.showMessage("--- Your seed collection ---");
        PlantRegistry registry = PlantRegistry.getInstance();
        for (Map.Entry<String, Integer> entry : owned.entrySet()) {
            String plantName = entry.getKey();
            PlantTemplate template = registry.getTemplateByName(plantName);
            String line = template == null ? entry.getKey() : formatPlant(template);
            OutputHandler.showMessage(line + " | owned: " + entry.getValue() +
                    " | level: " + profile.getPlantsLevels().getOrDefault(plantName, 1));
        }

    }
    public void renderPlantDetails(PlantRegistry registry, String plantName){
        PlantTemplate template = registry.getTemplateByName(plantName);
        if (template == null) {
            OutputHandler.showError("No such plant as '" + plantName + "'. Check the almanac!");
            return;
        }
        OutputHandler.showMessage("--- Almanac entry ---");
        OutputHandler.showMessage("name: " + template.getName());
        OutputHandler.showMessage("category: " + template.getCategory());
        OutputHandler.showMessage("cost: " + template.getCost());
        OutputHandler.showMessage("recharge: " + template.getRecharge());
        OutputHandler.showMessage("base hp: " + template.getBaseHp());
        OutputHandler.showMessage("damage: " + template.getDamage());
        OutputHandler.showMessage("base ability: " + template.getAbilityType());
        OutputHandler.showMessage("plant food: " + formatPlantFood(template));
    }

    private String formatPlantFood(PlantTemplate template) {
        if (template.getPlantFood() == null || template.getPlantFood().isEmpty()) {
            return "none";
        }
        StringBuilder effects = new StringBuilder();
        for (PlantTemplate.PlantFoodSpec spec : template.getPlantFood()) {
            if (effects.length() > 0) {
                effects.append(", ");
            }
            effects.append(spec.getType());
        }
        return effects.toString();
    }
    public void renderAllZombies(ZombieRegistry registry){
        Map<String, ZombieTemplate> all = registry.getZombieTemplatesByAlias();
        OutputHandler.showMessage("--- Know your enemy: every zombie on file ---");
        if (all == null || all.isEmpty()) {
            OutputHandler.showMessage("Not a zombie on file. Suspiciously quiet out there...");
            return;
        }
        for (ZombieTemplate template : all.values()) {
            OutputHandler.showMessage(formatZombie(template));
        }
    }
    public void renderSeenZombies(Profile profile){
        Set<String> seen = profile.getSeenZombieAliases();
        OutputHandler.showMessage("--- Zombies you've met (and survived) ---");
        if (seen == null || seen.isEmpty()) {
            OutputHandler.showMessage("You haven't met a single zombie yet. Enjoy it while it lasts.");
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
            OutputHandler.showError("No zombie called '" + zombieName + "' has ever shambled by.");
            return;
        }
        OutputHandler.showMessage("--- Almanac entry ---");
        OutputHandler.showMessage("alias: " + template.getAlias());
        OutputHandler.showMessage("objclass: " + template.getObjclass());
        OutputHandler.showMessage("base hp: " + template.getBaseHp());
        OutputHandler.showMessage("armor: " + template.getArmors());
        OutputHandler.showMessage("eat dps: " + template.getEatDps());
        OutputHandler.showMessage("speed: " + template.getSpeed());
        OutputHandler.showMessage("wave point cost: " + template.getWavePointCost());
    }
    public void notExist(String plantName){
        OutputHandler.showError("No such plant as '" + plantName + "'. Check the almanac!");}
    public void alreadyOwned(String plantName){
        OutputHandler.showError("You already grow '" + plantName + "'. One is plenty!");
    }
    public void notEnoughCoinToPurchase(String plantName){
        OutputHandler.showError("Not enough coins for '" + plantName + "'. Time to go farming!");
    }
    public void notOwned(String plantName){
        OutputHandler.showError("You don't own '" + plantName + "' yet -- buy it before you upgrade it.");
    }
    public void notEnoughSeed(String plantName){
        OutputHandler.showError("Not enough seed packets to upgrade '" + plantName + "'. Keep collecting!");
    }
    public void notEnoughCoinToUpgrade(String plantName){
        OutputHandler.showError("Not enough coins to upgrade '" + plantName + "'. Sunflowers don't grow "
                + "on trees... wait.");
    }
    public void successfulUpgrade(String plantName){
        OutputHandler.showSuccess("'" + plantName + "' leveled up! It's looking meaner already.");
    }
    public void successOfPurchasePlant(){
        OutputHandler.showSuccess("Sold! A new sprout joins your garden.");
    }
    public void plantMaxLevel(String plantName){
        OutputHandler.showError(String.format("'%s' is already maxed out. It cannot get any meaner.",
                plantName));
    }
}
