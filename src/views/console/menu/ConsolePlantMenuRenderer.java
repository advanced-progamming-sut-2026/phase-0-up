package views.console.menu;

import models.game.GameSession;
import models.templates.PlantTemplate;
import models.user.Profile;
import utils.registry.PlantRegistry;
import views.OutputHandler;
import views.renderers.MenuRenderer.PlantMenuRenderer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConsolePlantMenuRenderer implements PlantMenuRenderer {
    @Override
    public void renderAllPlants(PlantRegistry registry){
        Map<String, PlantTemplate> all = registry.getAllPlantTemplates();
        OutputHandler.showMessage("--- Every plant in the almanac ---");
        for (PlantTemplate template : all.values()) {
            OutputHandler.showMessage(formatTemplate(template));
        }
    }

    @Override
    public void renderAvailablePlants(GameSession session){
        List<String> available = availablePlantNames(session);
        if (available.isEmpty()) {
            OutputHandler.showMessage("Not a single plant is available here. Bare-handed it is!");
            return;
        }
        OutputHandler.showMessage("--- Ready to fight on this lawn ---");
        for (String plantName : available) {
            OutputHandler.showMessage(describeAvailablePlant(session, plantName));
        }
    }

    private List<String> availablePlantNames(GameSession session) {
        Profile profile = session.getPlayer();
        List<String> profilePlants = profile.getUnlockedPlants();
        // The Level owns the pool. Generated levels (scoring game, Zombotany) have no template at all,
        // so reading it through getTemplate() crashed the moment one of them opened seed selection.
        List<String> levelPlants = session.getLevel().getAvailablePlants();
        // The profile stores plant names lower-cased while the level pool uses display names, so the
        // two are matched case-insensitively; the level's spelling is kept for display.
        return levelPlants == null ? List.of() : levelPlants.stream()
                .filter(levelPlant -> profilePlants.stream().anyMatch(owned -> owned.equalsIgnoreCase(levelPlant)))
                .collect(Collectors.toList());
    }

    private String describeAvailablePlant(GameSession session, String plantName) {
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(plantName);
        String line = template == null ? plantName : formatTemplate(template);
        boolean forced = session.getMode() != null && session.getMode().isSeedForced(plantName);
        if (session.isSeedSelected(plantName)) {
            boolean boosted = session.getSelectedSeed(plantName).isBoosted();
            line += forced
                    ? (boosted ? "  [bolted down, boosted]" : "  [bolted down]")
                    : (boosted ? "  [selected, boosted]" : "  [selected]");
        } else if (!isOwned(session.getPlayer(), plantName)) {
            line += "  [locked]";
        }
        return line;
    }

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

    @Override
    public void plantNotSelected(String seedName){
        OutputHandler.showError("'" + seedName + "' isn't in your loadout, so there's nothing to do to it.");}

    @Override
    public void alreadyBoosted(String seedName){
        OutputHandler.showError("'" + seedName + "' is already fizzing with plant food. Save your gems!");}

    @Override
    public void notEnoughGem(){
        OutputHandler.showError("Not enough gems for a boost. Those things are precious.");}

    @Override
    public void successfulBoost(String seedName){
        OutputHandler.showSuccess("'" + seedName + "' is supercharged -- it'll fire off its plant food "
                + "the moment you plant it!");}

    @Override
    public void gameStarted(){
        OutputHandler.showSuccess("The lawn is set. Here they come -- good luck out there!");}

    @Override
    public void notExist(String plantName){
        OutputHandler.showError("No such plant as '" + plantName + "'. Check the almanac!");}

    @Override
    public void isLocked(String plantName){
        OutputHandler.showError("'" + plantName + "' is still locked. Unlock it in the collection first.");}

    // Owned, but this level does not field it -- a different refusal from isLocked.
    @Override
    public void notAvailableInLevel(String plantName){
        OutputHandler.showError("'" + plantName + "' isn't one of the plants this level offers. "
                + "Check the level's plant list and pick from there.");}

    @Override
    public void alreadySelected(String plantName){
        OutputHandler.showError("'" + plantName + "' is already on the seed bar.");}

    @Override
    public void noEmptySlot(){
        OutputHandler.showError("Seed bar is full! Drop something before you pick up another.");}

    @Override
    public void successfulAdd(String plantName){
        OutputHandler.showSuccess("'" + plantName + "' loaded onto the seed bar.");}

    @Override
    public void notSelected(String plantName){
        OutputHandler.showError("'" + plantName + "' isn't on the seed bar.");}

    @Override
    public void successfulRemove(String plantName){
        OutputHandler.showSuccess("'" + plantName + "' taken off the seed bar.");}

    @Override
    public void forcedSeedCannotAdd(String plantName){
        OutputHandler.showError("'" + plantName + "' is bolted to your seed bar by Locked Plants -- "
                + "it's already loaded, and you can't add it again.");}

    @Override
    public void forcedSeedCannotRemove(String plantName){
        OutputHandler.showError("'" + plantName + "' is bolted to your seed bar by Locked Plants -- "
                + "this lawn insists you bring it, so it can't come off.");}

    // Fallback for a mode that pins a seed without marking it forced; "still locked" was plainly wrong.
    @Override
    public void seedPinnedByMode(String plantName){
        OutputHandler.showError("'" + plantName + "' is pinned to your seed bar by this level's rules -- "
                + "it isn't coming off.");}
}
