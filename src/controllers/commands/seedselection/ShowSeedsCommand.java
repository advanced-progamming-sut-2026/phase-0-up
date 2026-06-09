package controllers.commands.seedselection;

import controllers.commands.Command;
import models.game.GameSession;

public class ShowSeedsCommand implements Command {
    private GameSession gameSession;
    private boolean showAllSeeds;

    @Override
    public void execute() {
    }
}
