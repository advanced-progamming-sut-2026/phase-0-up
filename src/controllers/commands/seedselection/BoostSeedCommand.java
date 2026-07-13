package controllers.commands.seedselection;

import controllers.commands.Command;
import models.templates.PlantTemplate;
import models.user.AppSession;
import models.user.Profile;
import models.user.User;
import utils.Constants;
import utils.registry.PlantRegistry;
import utils.storage.DatabaseManager;
import views.renderers.MenuRenderer.PlantMenuRenderer;

import java.util.Map;

public class BoostSeedCommand implements Command {
    private String seedName;
    private AppSession appSession;
    private PlantMenuRenderer renderer;
    public BoostSeedCommand(String seedName, AppSession appSession, PlantMenuRenderer renderer) {
        this.seedName = seedName;
        this.appSession = appSession;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(seedName);
        if(template == null){
            return;
        }
        User user = appSession.getCurrentUser();
        Profile profile = user.getProfile();
        Map<String, Integer> seeds = profile.getOwnedSeedPackets();
        String key = seedName.toLowerCase().trim();
        if (seeds == null || seeds.getOrDefault(key, 0) <= 0) {
            renderer.plantNotSelected(seedName);
            return;
        }
        if (profile.isSeedBoosted(seedName)) {
            renderer.alreadyBoosted(seedName);
            return;
        }
        if(profile.getGems() < Constants.BOOST_PLANT_COST_GEMS){
            renderer.notEnoughGem();
            return;
        }
        profile.spendGems(Constants.BOOST_PLANT_COST_GEMS);
        profile.setSeedBoosted(seedName, true);
        renderer.successfulBoost(seedName);

        DatabaseManager.getInstance().saveAll();
    }
}
