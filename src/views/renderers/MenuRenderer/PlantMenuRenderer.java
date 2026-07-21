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
        OutputHandler.showMessage("--- Every plant in the almanac ---");
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
            OutputHandler.showMessage("Not a single plant is available here. Bare-handed it is!");
            return;
        }
        OutputHandler.showMessage("--- Ready to fight on this lawn ---");
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
    public void plantNotSelected(String seedName){
        OutputHandler.showError("'" + seedName + "' isn't in your loadout, so there's nothing to do to it.");}
    public void alreadyBoosted(String seedName){
        OutputHandler.showError("'" + seedName + "' is already fizzing with plant food. Save your gems!");}
    public void notEnoughGem(){
        OutputHandler.showError("Not enough gems for a boost. Those things are precious.");}
    public void successfulBoost(String seedName){
        OutputHandler.showSuccess("'" + seedName + "' is supercharged -- it'll fire off its plant food "
                + "the moment you plant it!");}
    public void gameStarted(){
        OutputHandler.showSuccess("The lawn is set. Here they come -- good luck out there!");}
    public void notExist(String plantName){
        OutputHandler.showError("No such plant as '" + plantName + "'. Check the almanac!");}
    public void isLocked(String plantName){
        OutputHandler.showError("'" + plantName + "' is still locked. Unlock it in the collection first.");}
    public void alreadySelected(String plantName){
        OutputHandler.showError("'" + plantName + "' is already on the seed bar.");}
    public void noEmptySlot(){
        OutputHandler.showError("Seed bar is full! Drop something before you pick up another.");}
    public void successfulAdd(String plantName){
        OutputHandler.showSuccess("'" + plantName + "' loaded onto the seed bar.");}
    public void notSelected(String plantName){
        OutputHandler.showError("'" + plantName + "' isn't on the seed bar.");}
    public void successfulRemove(String plantName){
        OutputHandler.showSuccess("'" + plantName + "' taken off the seed bar.");}
}
