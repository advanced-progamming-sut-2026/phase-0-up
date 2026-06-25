package controllers.commands.seedselection;

import controllers.commands.Command;
import models.game.GameSession;
import utils.registry.PlantRegistry;
import views.renderers.MenuRenderer.PlantMenuRenderer;

public class ShowSeedsCommand implements Command {
    private GameSession gameSession;
    private boolean showAllSeeds;
    private PlantMenuRenderer renderer;

    public ShowSeedsCommand(GameSession gameSession, boolean showAllSeeds, PlantMenuRenderer renderer) {
        this.gameSession = gameSession;
        this.showAllSeeds = showAllSeeds;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        if(showAllSeeds){
            renderer.renderAllPlants(PlantRegistry.getInstance());
        }else{
            renderer.renderAvailablePlants(gameSession);
        }
    }
}
