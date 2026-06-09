package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;

public class FeedPlantCommand implements Command {
    private GameSession gameSession;
    private int tileX;
    private int tileY;

    @Override
    public void execute() {}
}
