package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;
import views.renderers.InGameRenderer;

// "swap -l (x1, y1) (x2, y2)": swaps two adjacent plants in the Beghouled match-3 mini-game.
public class SwapPlantsCommand implements Command {
    private final GameSession gameSession;
    private final InGameRenderer renderer;
    private final int x1;
    private final int y1;
    private final int x2;
    private final int y2;

    public SwapPlantsCommand(GameSession gameSession, InGameRenderer renderer,
                             int x1, int y1, int x2, int y2) {
        this.gameSession = gameSession;
        this.renderer = renderer;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    @Override
    public void execute() {
        renderer.render(gameSession.swapPlants(x1, y1, x2, y2));
    }
}
