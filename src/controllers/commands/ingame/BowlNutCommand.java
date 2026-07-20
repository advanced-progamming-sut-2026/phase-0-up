package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;
import views.renderers.InGameRenderer;

// "bowl -t <type> -l (x, y)": bowls a conveyor nut (bowling / explode / giant) down a row from behind
// the red line in the Wall-nut Bowling mini-game.
public class BowlNutCommand implements Command {
    private final GameSession gameSession;
    private final InGameRenderer renderer;
    private final String nutType;
    private final int tileX;
    private final int tileY;

    public BowlNutCommand(GameSession gameSession, InGameRenderer renderer, String nutType,
                          int tileX, int tileY) {
        this.gameSession = gameSession;
        this.renderer = renderer;
        this.nutType = nutType;
        this.tileX = tileX;
        this.tileY = tileY;
    }

    @Override
    public void execute() {
        renderer.render(gameSession.bowlNut(nutType, tileX, tileY));
    }
}
