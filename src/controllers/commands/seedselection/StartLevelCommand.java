package controllers.commands.seedselection;

import controllers.commands.Command;
import controllers.engine.InputRouter;
import controllers.engine.MenuType;
import models.game.GameSession;
import models.game.gamemodes.GameMode;
import views.OutputHandler;
import views.renderers.MenuRenderer.PlantMenuRenderer;

public class StartLevelCommand implements Command {
    private GameSession gameSession;
    private InputRouter inputRouter;

    public StartLevelCommand(GameSession gameSession, InputRouter inputRouter) {
        this.gameSession = gameSession;
        this.inputRouter = inputRouter;
    }
    @Override
    public void execute() {
        PlantMenuRenderer renderer = new PlantMenuRenderer();
        GameMode mode = gameSession.getMode();
        if(mode != null){
            mode.onStart(gameSession);
        }
        inputRouter.setCurrentMenu(MenuType.IN_GAME);
        renderer.gameStarted();
    }
}
