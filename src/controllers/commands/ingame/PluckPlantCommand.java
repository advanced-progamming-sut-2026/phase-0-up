package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;
import views.renderers.InGameRenderer;

public class PluckPlantCommand implements Command {
    private GameSession gameSession;
    private final InGameRenderer renderer;
    private int tileX;
    private int tileY;

    public PluckPlantCommand(GameSession gameSession, InGameRenderer renderer, int tileX, int tileY) {
        this.gameSession = gameSession;
        this.renderer = renderer;
        this.tileX = tileX;
        this.tileY = tileY;
    }

    @Override
    public void execute() {
        renderer.render(gameSession.pluck(tileX , tileY));
    }
}
