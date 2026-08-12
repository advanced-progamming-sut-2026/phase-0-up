package controllers.commands.seedselection;

import controllers.commands.Command;
import controllers.engine.MenuType;
import models.game.GameSession;
import models.user.AppSession;
import views.renderers.MenuRenderer.PlantMenuRenderer;

public class StartLevelCommand implements Command {
    private GameSession gameSession;
    private AppSession appSession;
    // Injected rather than built here. This was the one Command that constructed its own renderer with
    // `new`, which is precisely the coupling that stopped a second front end from existing: a class it
    // instantiates directly can never be swapped for another.
    private final PlantMenuRenderer renderer;

    public StartLevelCommand(GameSession gameSession, AppSession appSession,
                             PlantMenuRenderer renderer) {
        this.gameSession = gameSession;
        this.appSession = appSession;
        this.renderer = renderer;
    }
    @Override
    public void execute() {
        // The mode is started by GameEngine.startLoop (via GameSession.startMode), which runs right
        // after this command. Starting it here too fired every mode's onStart twice -- Save Our Seeds
        // would try to pre-place its protected plants on top of themselves.
        appSession.setCurrentMenu(MenuType.IN_GAME);
        renderer.gameStarted();
    }
}
