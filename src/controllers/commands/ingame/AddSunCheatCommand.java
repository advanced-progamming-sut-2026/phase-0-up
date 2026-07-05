package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;
import utils.Result;
import views.renderers.InGameRenderer;

public class AddSunCheatCommand implements Command {
    private GameSession gameSession;
    private final InGameRenderer renderer;
    private int count;

    public AddSunCheatCommand(GameSession gameSession, InGameRenderer renderer, int count) {
        this.gameSession = gameSession;
        this.renderer = renderer;
        this.count = count;
    }

    @Override
    public void execute() {
        if(count <= 0){
            renderer.render(new Result(false , "Sun count must be positive."));
            return;
        }
        gameSession.increaseSunAmount(count);
        renderer.render(new Result(true ,
                "Added " + count + " suns. Current amount: " + gameSession.getSunAmount()));
    }
}
