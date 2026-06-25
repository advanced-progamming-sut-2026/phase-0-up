package controllers.commands.seedselection;

import controllers.commands.Command;
import models.game.GameSession;
import models.game.SeedPacket;
import models.templates.LevelTemplate;
import models.templates.PlantTemplate;
import models.user.Profile;
import utils.registry.PlantRegistry;
import views.renderers.MenuRenderer.PlantMenuRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToggleSeedCommand implements Command {
    private ToggleAction action;
    private GameSession gameSession;
    private String plantName;
    private PlantMenuRenderer renderer;


    public ToggleSeedCommand(ToggleAction action, String plantName, GameSession gameSession, PlantMenuRenderer renderer) {
        this.action = action;
        this.plantName = plantName;
        this.gameSession = gameSession;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(plantName);
        if(action == ToggleAction.ADD){
            add(template);
        }else{
            remove(template);
        }
    }

    private boolean isUnlockedAndAvailable() {
        Profile profile = gameSession.getPlayer();
        Map<SeedPacket, Integer> map = profile.getOwnedSeedPackets();
        Map<String, Integer> owned = null;

        if (map != null) {
            owned = new HashMap<>();
            for (Map.Entry<SeedPacket, Integer> entry : map.entrySet()) {
                owned.put(entry.getKey().getPlantType(), entry.getValue());
            }
        }

        boolean unlocked = owned != null && owned.getOrDefault(plantName, 0) > 0;
        LevelTemplate levelTemplate = gameSession.getLevel().getTemplate();
        List<String> available = levelTemplate.getAvailablePlants();
        boolean allowedInLevel = available != null && available.contains(plantName);
        return unlocked && allowedInLevel;
    }

    private void add(PlantTemplate template){
        if (template == null) {
            renderer.notExist(plantName);
            return;
        }
        if (!isUnlockedAndAvailable()) {
            renderer.isLocked(plantName);
            return;
        }
        if (gameSession.isSeedSelected(plantName)) {
            renderer.alreadySelected(plantName);
            return;
        }
        if (gameSession.getSelectedSeeds().size() >= gameSession.getMaxSeedSlots()) {
            renderer.noEmptySlot();
            return;
        }
        gameSession.addSeed(new SeedPacket(plantName, template.getRecharge()));
        renderer.successfulAdd(plantName);
    }


    private void remove(PlantTemplate template){
        if(template == null){
            renderer.notExist(plantName);
            return;
        }
        if(!gameSession.isSeedSelected(plantName)){
            renderer.notSelected(plantName);
            return;
        }
        gameSession.removeSeed(plantName);
        renderer.successfulRemove(plantName);
    }

}
