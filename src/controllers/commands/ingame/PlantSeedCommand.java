package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;
import views.renderers.InGameRenderer;

public class PlantSeedCommand implements Command {
    private GameSession gameSession;
    private final InGameRenderer renderer;
    private String plantType;
    private int tileX;
    private int tileY;

    public PlantSeedCommand(GameSession gameSession, InGameRenderer renderer, String plantType,
                            int tileX, int tileY) {
        this.gameSession = gameSession;
        this.renderer = renderer;
        this.plantType = plantType;
        this.tileX = tileX;
        this.tileY = tileY;
    }

    @Override
    public void execute() {
        renderer.render(gameSession.plant(tileX, tileY, plantType));
    }
}
