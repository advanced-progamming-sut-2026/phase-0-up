package controllers.commands.seedselection;

import controllers.commands.Command;
import controllers.engine.InputRouter;
import controllers.engine.MenuType;
import models.game.GameSession;
import models.game.gamemodes.GameMode;
import models.user.AppSession;
import views.OutputHandler;
import views.renderers.MenuRenderer.PlantMenuRenderer;

public class StartLevelCommand implements Command {
    private GameSession gameSession;
    private AppSession appSession;

    public StartLevelCommand(GameSession gameSession, AppSession appSession) {
        this.gameSession = gameSession;
        this.appSession = appSession;
    }
    @Override
    public void execute() {
        PlantMenuRenderer renderer = new PlantMenuRenderer();
        GameMode mode = gameSession.getMode();
        if(mode != null){
            mode.onStart(gameSession);
        }
        appSession.setCurrentMenu(MenuType.IN_GAME);
        renderer.gameStarted();
    }
}
