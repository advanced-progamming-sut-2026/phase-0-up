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
        // Checked before ownership and slot limits: a seed the mode bolted down is refused for that
        // reason whatever else is true of it, rather than being reported as merely locked or duplicate.
        if (isForcedByMode()) {
            renderer.forcedSeedCannotAdd(plantName);
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
        // Locked Plants pins its forced seeds; every other mode allows removal. Checked first so the
        // refusal names the real reason instead of the generic "not on the seed bar" / "locked".
        if(isForcedByMode()){
            renderer.forcedSeedCannotRemove(plantName);
            return;
        }
        if(!gameSession.isSeedSelected(plantName)){
            renderer.notSelected(plantName);
            return;
        }
        if(gameSession.getMode() != null && !gameSession.getMode().isSeedRemovable(plantName)){
            renderer.isLocked(plantName);
            return;
        }
        gameSession.removeSeed(plantName);
        renderer.successfulRemove(plantName);
    }

    // Whether the level's mode put this seed on the bar itself, making it neither addable nor removable.
    private boolean isForcedByMode(){
        return gameSession.getMode() != null && gameSession.getMode().isSeedForced(plantName);
    }
}
