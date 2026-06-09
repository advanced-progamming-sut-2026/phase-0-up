package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;

public class ShowMapStatusCommand implements Command {
    private ShowMapStatusAction action;
    private GameSession gameSession;
    private int tileX;
    private int tileY;

    @Override
    public void execute() {}
}
