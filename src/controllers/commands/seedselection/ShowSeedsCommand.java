package controllers.commands.seedselection;

import controllers.commands.Command;
import models.game.GameSession;
import utils.registry.PlantRegistry;
import views.renderers.MenuRenderer.PlantMenuRenderer;

public class ShowSeedsCommand implements Command {
    private GameSession gameSession;
    private boolean showAllSeeds;

    public ShowSeedsCommand(GameSession gameSession, boolean showAllSeeds) {
        this.gameSession = gameSession;
        this.showAllSeeds = showAllSeeds;
    }

    @Override
    public void execute() {
        PlantMenuRenderer renderer = new PlantMenuRenderer();
        if(showAllSeeds){
            renderer.renderAllPlants(PlantRegistry.getInstance());
        }else{
            renderer.renderAvailablePlants(gameSession);
        }
    }
}
