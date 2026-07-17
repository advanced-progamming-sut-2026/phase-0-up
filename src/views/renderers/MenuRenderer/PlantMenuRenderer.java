package views.renderers.MenuRenderer;

import models.game.GameSession;
import models.templates.LevelTemplate;
import models.templates.PlantTemplate;
import models.user.Profile;
import utils.registry.PlantRegistry;
import views.OutputHandler;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PlantMenuRenderer {
    public void renderAllPlants(PlantRegistry registry){
        Map<String, PlantTemplate> all = registry.getAllPlantTemplates();
        OutputHandler.showMessage("All plants:");
        for (PlantTemplate template : all.values()) {
            OutputHandler.showMessage(formatTemplate(template));
        }

    }
    public void renderAvailablePlants(GameSession session){
        Profile profile = session.getPlayer();
        PlantRegistry registry = PlantRegistry.getInstance();
        LevelTemplate levelTemplate = session.getLevel().getTemplate();
        List<String> profilePlants = profile.getUnlockedPlants();
        List<String> levelPlants = levelTemplate.getAvailablePlants();
        // The profile stores plant names lower-cased while the level pool uses display names, so the
        // two are matched case-insensitively; the level's spelling is kept for display.
        List<String> available = levelPlants == null ? List.of() : levelPlants.stream()
                .filter(levelPlant -> profilePlants.stream().anyMatch(owned -> owned.equalsIgnoreCase(levelPlant)))
                .collect(Collectors.toList());


        if (available.isEmpty()) {
            OutputHandler.showMessage("No plants are available in this level.");
            return;
        }
        OutputHandler.showMessage("Available plants in this level:");
        for (String plantName : available) {
            PlantTemplate template = registry.getTemplateByName(plantName);
            String line = template == null ? plantName : formatTemplate(template);
            if (session.isSeedSelected(plantName)) {
                line += session.getSelectedSeed(plantName).isBoosted()
                        ? "  [selected, boosted]" : "  [selected]";
            } else if (!isOwned(profile, plantName)) {
                line += "  [locked]";
            }
            OutputHandler.showMessage(line);
        }
    }
    public void addPlant(String plantName){

    }
    public void removePlant(String plantName){}
    public void boostPlant(){}
    public void startGame(){}


    private String formatTemplate(PlantTemplate template) {
        return template.getName()
                + " | cost: " + template.getCost()
                + " | recharge: " + template.getRecharge();
    }

    private boolean isOwned(Profile profile, String plantName) {
        Map<String, Integer> owned = profile.getOwnedSeedPackets();
        if (owned == null) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : owned.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(plantName) && entry.getValue() > 0) {
                return true;
            }
        }
        return false;
    }
    public void plantNotSelected(String seedName){OutputHandler.showError("Plant '" + seedName + "' is not selected.");}
    public void alreadyBoosted(String seedName){OutputHandler.showError("Plant '" +seedName + "' is already boosted.");}
    public void notEnoughGem(){OutputHandler.showError("Not enough gems to boost this plant.");}
    public void successfulBoost(String seedName){OutputHandler.showSuccess("Plant '" + seedName + "' has been boosted.");}
    public void gameStarted(){OutputHandler.showSuccess("Game started.");}
    public void notExist(String plantName){OutputHandler.showError("Plant '" + plantName +"' does not exist.");}
    public void isLocked(String plantName){OutputHandler.showError("Plant '" + plantName + "' is locked.");}
    public void alreadySelected(String plantName){OutputHandler.showError("Plant '"+plantName+"' is already selected.");}
    public void noEmptySlot(){OutputHandler.showError("There is no empty seed slot.");}
    public void successfulAdd(String plantName){OutputHandler.showSuccess("Plant '" + plantName + "' added to your seeds.");}
    public void notSelected(String plantName){OutputHandler.showError("Plant '" + plantName +"' is not selected");}
    public void successfulRemove(String plantName){OutputHandler.showSuccess("Plant '" + plantName + "' removed from your seeds");}
}
