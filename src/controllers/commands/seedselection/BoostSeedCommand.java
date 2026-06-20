package controllers.commands.seedselection;

import controllers.commands.Command;
import models.game.GameSession;
import models.game.SeedPacket;
import models.templates.PlantTemplate;
import models.user.Profile;
import utils.Constants;
import utils.registry.PlantRegistry;
import views.renderers.MenuRenderer.PlantMenuRenderer;

public class BoostSeedCommand implements Command {
    private String seedName;
    private GameSession gameSession;

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
        SeedPacket seed = gameSession.getSelectedSeeds().getFirst();
        if(seed == null){
            renderer.plantNotSelected(seedName);
            return;
        }
        if(seed.isBoosted()){
            renderer.alreadyBoosted(seedName);
            return;
        }
        Profile profile = gameSession.getPlayer();
        if(profile.getGems() < Constants.BOOST_PLANT_COST_GEMS){
            renderer.notEnoughGem();
            return;
        }
        profile.spendGems(Constants.BOOST_PLANT_COST_GEMS);
        seed.setBoosted(true);
        renderer.successfulBoost(seedName);
    }
}
