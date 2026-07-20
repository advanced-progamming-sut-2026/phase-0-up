package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;
import views.renderers.InGameRenderer;

// "break vase -l (x, y)": smashes the Vasebreaker vase on that tile, revealing whatever it hid.
public class BreakVaseCommand implements Command {
    private final GameSession gameSession;
    private final InGameRenderer renderer;
    private final int tileX;
    private final int tileY;

    public BreakVaseCommand(GameSession gameSession, InGameRenderer renderer, int tileX, int tileY) {
        this.gameSession = gameSession;
        this.renderer = renderer;
        this.tileX = tileX;
        this.tileY = tileY;
    }

    @Override
    public void execute() {
        renderer.render(gameSession.breakVase(tileX, tileY));
    }
}
