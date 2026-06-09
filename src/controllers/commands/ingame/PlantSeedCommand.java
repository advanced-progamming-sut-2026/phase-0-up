package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;

public class PlantSeedCommand implements Command {
    private GameSession gameSession;
    private String plantType;
    private int tileX;
    private int tileY;

    @Override
    public void execute() {

    }
}
