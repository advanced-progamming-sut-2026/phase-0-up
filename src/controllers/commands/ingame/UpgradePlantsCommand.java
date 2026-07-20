package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;
import views.renderers.InGameRenderer;

// "upgrade -t <plant>": spends sun to upgrade every plant of one type on the board (Beghouled).
public class UpgradePlantsCommand implements Command {
    private final GameSession gameSession;
    private final InGameRenderer renderer;
    private final String plantType;

    public UpgradePlantsCommand(GameSession gameSession, InGameRenderer renderer, String plantType) {
        this.gameSession = gameSession;
        this.renderer = renderer;
        this.plantType = plantType;
    }

    @Override
    public void execute() {
        renderer.render(gameSession.upgradePlant(plantType));
    }
}
