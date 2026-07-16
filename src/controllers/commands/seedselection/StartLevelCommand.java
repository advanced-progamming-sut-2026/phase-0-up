package controllers.commands.seedselection;

import controllers.commands.Command;
import controllers.engine.MenuType;
import models.game.GameSession;
import models.user.AppSession;
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
        // The mode is started by GameEngine.startLoop (via GameSession.startMode), which runs right
        // after this command. Starting it here too fired every mode's onStart twice -- Save Our Seeds
        // would try to pre-place its protected plants on top of themselves.
        appSession.setCurrentMenu(MenuType.IN_GAME);
        renderer.gameStarted();
    }
}
