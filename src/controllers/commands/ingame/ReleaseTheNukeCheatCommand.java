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
            renderer.render(new Result(true, "The lawn is already spotless. Nothing to nuke!"));
            return;
        }

        // Same wording as CombatSystem.processDeaths, which is the spec's: no trailing period.
        for (Zombie zombie : killedZombies) {
            renderer.render(new Result(true, "Zombie of type " + zombie.getAlias() + " is dead at ("
                    + (int) zombie.getMovement().getPositionX() + ", " + zombie.getMovement().getPositionY() + ")"));
        }
        renderer.render(new Result(true, "KA-BOOM! " + killedZombies.size()
                + " zombies turned to compost."));
    }
}
