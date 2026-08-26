package controllers.commands.seedselection;

import controllers.commands.Command;
import models.game.GameSession;
import models.game.SeedPacket;
import models.templates.PlantTemplate;
import models.user.Profile;
import utils.registry.PlantRegistry;
import views.renderers.MenuRenderer.PlantMenuRenderer;

import java.util.List;

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

    // Separate from isAllowedInLevel(): "still locked" sent players to unlock what they owned.
    //
    // Asks the UNLOCKED list, not the seed-packet counts. Packets are the upgrade currency:
    // CollectionSystem.upgradePlant spends five of them, so a plant sitting on exactly five became a
    // plant with zero the moment it was upgraded -- and a count of zero read here as "never unlocked".
    // Upgrading a plant made it unusable, which is the exact opposite of what upgrading is for, and it
    // hit the plants a player invests in hardest. Ownership is the unlocked list; that is also what
    // SeedSelectionScreen draws its grid from, so the screen and the command now agree.
    private boolean isUnlocked() {
        Profile profile = gameSession.getPlayer();
        List<String> owned = profile == null ? null : profile.getUnlockedPlants();
        if (owned == null) {
            return false;
        }
        // Profile keys are lower-cased while the level pool uses display names -> compare ignoring case.
        return owned.stream().anyMatch(name -> name != null && name.equalsIgnoreCase(plantName));
    }
    private boolean isAllowedInLevel() {
        // Read the pool off the Level, not its template: a level built without a template (the
        // scoring game, Zombotany and the other generated levels) carries its plant pool directly, and
        // going through the template dereferenced null and crashed seed selection outright.
        List<String> available = gameSession.getLevel().getAvailablePlants();
        return available != null
                && available.stream().anyMatch(p -> p.equalsIgnoreCase(plantName));
    }

    private void add(PlantTemplate template){
        if (template == null) {
            renderer.notExist(plantName);
            return;
        }
        if (isForcedByMode()) {
            renderer.forcedSeedCannotAdd(plantName);
            return;
        }
        if (!isUnlocked()) {
            renderer.isLocked(plantName);
            return;
        }
        if (!isAllowedInLevel()) {
            renderer.notAvailableInLevel(plantName);
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
        if(isForcedByMode()){
            renderer.forcedSeedCannotRemove(plantName);
            return;
        }
        if(!gameSession.isSeedSelected(plantName)){
            renderer.notSelected(plantName);
            return;
        }
        if(gameSession.getMode() != null && !gameSession.getMode().isSeedRemovable(plantName)){
            renderer.seedPinnedByMode(plantName);
            return;
        }
        gameSession.removeSeed(plantName);
        renderer.successfulRemove(plantName);
    }

    private boolean isForcedByMode(){
        return gameSession.getMode() != null && gameSession.getMode().isSeedForced(plantName);
    }
}
