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
        if (action == ToggleAction.IMITATE) {
            imitate(template);
        } else if (action == ToggleAction.ADD) {
            add(template);
        } else {
            remove(template);
        }
    }

    // ---- the Imitater ---------------------------------------------------------------------------
    //
    // The Imitater is not a plant you put on the lawn -- it is a second packet of something else, with
    // its own recharge, so you can have two Peashooters in the air at once. Picking it is therefore two
    // choices ("the Imitater" then "as what"), and this command is the second half of that: plantName
    // here is the plant being COPIED.
    //
    // Which plant is the Imitater is asked of the DATA rather than matched by name: it is the one whose
    // ability is MODIFIER_UTILITY, the type PlantAbilityFactory deliberately builds nothing for.
    private static boolean isImitater(PlantTemplate template) {
        return template != null
                && PlantRegistry.getInstance().isImitater(template.getName());
    }

    private void imitate(PlantTemplate copied) {
        if (copied == null) {
            renderer.notExist(plantName);
            return;
        }
        // The Imitater has to be in the loadout to be spent: it is one of the level's own seed choices,
        // and without this check it would be a free extra packet in every level that does not offer it.
        PlantTemplate imitater = imitaterTemplate();
        if (imitater == null || !isUnlockedName(imitater.getName())
                || !isAllowedInLevelName(imitater.getName())) {
            renderer.notAvailableInLevel(imitater == null ? "Imitater" : imitater.getName());
            return;
        }
        if (isImitater(copied)) {
            renderer.imitaterNeedsTarget();
            return;
        }
        SeedPacket existing = gameSession.getImitatedSeed();
        if (existing != null) {
            renderer.imitaterAlreadyUsed(existing.getPlantType());
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
        if (gameSession.getSelectedSeeds().size() >= gameSession.getMaxSeedSlots()) {
            renderer.noEmptySlot();
            return;
        }
        gameSession.addSeed(new SeedPacket(plantName, (int) Math.round(copied.getRecharge()), true));
        renderer.successfulImitate(plantName);
    }

    private PlantTemplate imitaterTemplate() {
        return PlantRegistry.getInstance().getImitaterTemplate();
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
        return isUnlockedName(plantName);
    }

    // By name rather than off the field, because the Imitater has to be checked alongside the plant it
    // is copying and only one of the two can be the command's own subject.
    private boolean isUnlockedName(String name) {
        Profile profile = gameSession.getPlayer();
        List<String> owned = profile == null ? null : profile.getUnlockedPlants();
        if (owned == null) {
            return false;
        }
        // Profile keys are lower-cased while the level pool uses display names -> compare ignoring case.
        return owned.stream().anyMatch(n -> n != null && n.equalsIgnoreCase(name));
    }
    private boolean isAllowedInLevel() {
        return isAllowedInLevelName(plantName);
    }

    private boolean isAllowedInLevelName(String name) {
        // Read the pool off the Level, not its template: a level built without a template (the
        // scoring game, Zombotany and the other generated levels) carries its plant pool directly, and
        // going through the template dereferenced null and crashed seed selection outright.
        List<String> available = gameSession.getLevel().getAvailablePlants();
        return available != null
                && available.stream().anyMatch(p -> p.equalsIgnoreCase(name));
    }

    private void add(PlantTemplate template){
        if (template == null) {
            renderer.notExist(plantName);
            return;
        }
        // An Imitater on its own does nothing at all -- it has no ability and plants nothing. Picking
        // it is only ever the first half of a choice, so say so instead of handing over a dead card.
        if (isImitater(template)) {
            renderer.imitaterNeedsTarget();
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
        // "remove the Imitater" is the only way to take the copy off: the copy answers to the plant it
        // is imitating, so asking for that name would take the player's own packet instead.
        if (isImitater(template)) {
            if (gameSession.removeImitatedSeed()) {
                renderer.successfulRemove(template.getName());
            } else {
                renderer.notSelected(template.getName());
            }
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
