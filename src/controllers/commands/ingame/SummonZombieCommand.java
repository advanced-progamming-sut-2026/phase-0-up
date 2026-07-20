package controllers.commands.ingame;

import controllers.commands.Command;
import models.game.GameSession;
import views.renderers.InGameRenderer;

// "summon -t <type> -l (x, y)": summons one of the player's zombies to the right of the red line in
// the I, Zombie mini-game.
public class SummonZombieCommand implements Command {
    private final GameSession gameSession;
    private final InGameRenderer renderer;
    private final String zombieType;
    private final int tileX;
    private final int tileY;

    public SummonZombieCommand(GameSession gameSession, InGameRenderer renderer, String zombieType,
                               int tileX, int tileY) {
        this.gameSession = gameSession;
        this.renderer = renderer;
        this.zombieType = zombieType;
        this.tileX = tileX;
        this.tileY = tileY;
    }

    @Override
    public void execute() {
        renderer.render(gameSession.summonZombie(zombieType, tileX, tileY));
    }
}
