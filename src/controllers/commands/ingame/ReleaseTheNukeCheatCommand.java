package controllers.commands.ingame;

import controllers.commands.Command;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Result;
import views.renderers.InGameRenderer;

import java.util.List;

public class ReleaseTheNukeCheatCommand implements Command {
    private GameSession gameSession;
    private final InGameRenderer renderer;

    public ReleaseTheNukeCheatCommand(GameSession gameSession, InGameRenderer renderer) {
        this.gameSession = gameSession;
        this.renderer = renderer;
    }
    @Override
    public void execute() {
        List<Zombie> killedZombies = gameSession.getMap().killAllZombies();
        if (killedZombies.isEmpty()) {
            renderer.render(new Result(true, "There are no zombies on the map."));
            return;
        }

        for (Zombie zombie : killedZombies) {
            renderer.render(new Result(true, "Zombie of type " + zombie.getAlias() + " is dead at ("
                    + (int) zombie.getMovement().getPositionX() + ", " + zombie.getMovement().getPositionY() + ")."));
        }
        renderer.render(new Result(true,
                "The nuke has been released. " + killedZombies.size() + " zombies were destroyed."));
    }
}
