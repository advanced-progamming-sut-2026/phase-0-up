package controllers.commands.seedselection;

import controllers.commands.Command;
import models.game.GameSession;
import models.game.SeedPacket;
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


    public ToggleSeedCommand(ToggleAction action, String plantName, GameSession gameSession,
                             PlantMenuRenderer renderer) {
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
        Map<String, Integer> map = profile.getOwnedSeedPackets();
        Map<String, Integer> owned = null;

        if (map != null) {
            owned = new HashMap<>();
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                owned.put(entry.getKey(), entry.getValue());
            }
        }

        // Profile keys are lower-cased while the level pool uses display names -> compare ignoring case.
        boolean unlocked = owned != null && owned.entrySet().stream()
                .anyMatch(e -> e.getKey().equalsIgnoreCase(plantName) && e.getValue() > 0);
        // Read the pool off the Level, not its template: a level built without a template (the
        // scoring game, Zombotany and the other generated levels) carries its plant pool directly, and
        // going through the template dereferenced null and crashed seed selection outright.
        List<String> available = gameSession.getLevel().getAvailablePlants();
        boolean allowedInLevel = available != null
                && available.stream().anyMatch(p -> p.equalsIgnoreCase(plantName));
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
        gameSession.addSeed(new SeedPacket(plantName, (int) Math.round(template.getRecharge())));
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
        // Locked Plants can pin a seed in place; every other mode allows removal.
        if(gameSession.getMode() != null && !gameSession.getMode().isSeedRemovable(plantName)){
            renderer.isLocked(plantName);
            return;
        }
        gameSession.removeSeed(plantName);
        renderer.successfulRemove(plantName);
    }

}
