package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;
import utils.Result;
import views.renderers.InGameRenderer;

public class ShowSunCommand implements Command {
    private GameSession gameSession;
    private final InGameRenderer renderer;

    public ShowSunCommand(InGameRenderer renderer, GameSession gameSession) {
        this.renderer = renderer;
        this.gameSession = gameSession;
    }

    @Override
    public void execute() {
        renderer.render(new Result(true ,
                "Sun amount: " + gameSession.getSunAmount()));
    }
}
