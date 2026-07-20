package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;
import views.renderers.InGameRenderer;

// "collect seed -l (x, y)": picks up the seed packet a broken Vasebreaker vase dropped on that tile.
public class CollectSeedCommand implements Command {
    private final GameSession gameSession;
    private final InGameRenderer renderer;
    private final int tileX;
    private final int tileY;

    public CollectSeedCommand(GameSession gameSession, InGameRenderer renderer, int tileX, int tileY) {
        this.gameSession = gameSession;
        this.renderer = renderer;
        this.tileX = tileX;
        this.tileY = tileY;
    }

    @Override
    public void execute() {
        renderer.render(gameSession.collectSeed(tileX, tileY));
    }
}
