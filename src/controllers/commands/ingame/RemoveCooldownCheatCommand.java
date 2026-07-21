package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;
import utils.Result;
import views.renderers.InGameRenderer;

public class RemoveCooldownCheatCommand implements Command {
    private GameSession gameSession;
    private final InGameRenderer renderer;

    public RemoveCooldownCheatCommand(GameSession gameSession, InGameRenderer renderer) {
        this.gameSession = gameSession;
        this.renderer = renderer;
    }
    @Override
    public void execute() {
        gameSession.removeCooldownRestriction();
        renderer.render(new Result(true, "Cooldowns gone! Plant as fast as your fingers allow."));
    }
}
