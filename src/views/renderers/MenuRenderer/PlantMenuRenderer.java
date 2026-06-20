package views.renderers.MenuRenderer;

import models.game.GameSession;
import models.templates.LevelTemplate;
import models.templates.PlantTemplate;
import models.user.Profile;
import utils.registry.PlantRegistry;
import views.OutputHandler;

import java.util.List;
import java.util.Map;

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
        List<String> available = levelTemplate.getAvailablePlants();
        if (available == null || available.isEmpty()) {
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
        return owned != null && owned.getOrDefault(plantName, 0) > 0;
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
