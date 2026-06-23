package controllers.commands.seedselection;

import controllers.commands.Command;
import controllers.engine.InputRouter;
import models.game.GameSession;
import models.game.SeedPacket;
import models.templates.PlantTemplate;
import models.user.AppSession;
import models.user.Profile;
import models.user.User;
import utils.Constants;
import utils.registry.PlantRegistry;
import views.renderers.MenuRenderer.PlantMenuRenderer;

import java.util.List;
import java.util.Map;

public class BoostSeedCommand implements Command {
    private String seedName;
    private GameSession gameSession;
    private AppSession appSession;
    public BoostSeedCommand(String seedName, GameSession gameSession) {
        this.seedName = seedName;
        this.gameSession = gameSession;
    }

    @Override
    public void execute() {
        PlantMenuRenderer renderer = new PlantMenuRenderer();
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(seedName);
        if(template == null){
             return;
        }
        User user = appSession.getCurrentUser();
        Profile profile = user.getProfile();
        Map<SeedPacket, Integer> seeds = profile.getOwnedSeedPackets();
        SeedPacket seed = null;
        for(SeedPacket s: seeds.keySet()){
            if(s.getPlantType().equals(seedName)){
                seed = s;
            }
        }

        if(seed == null){
            renderer.plantNotSelected(seedName);
            return;
        }
        if(seed.isBoosted()){
            renderer.alreadyBoosted(seedName);
            return;
        }
        if(profile.getGems() < Constants.BOOST_PLANT_COST_GEMS){
            renderer.notEnoughGem();
            return;
        }
        profile.spendGems(Constants.BOOST_PLANT_COST_GEMS);
        seed.setBoosted(true);
        renderer.successfulBoost(seedName);
    }
}
