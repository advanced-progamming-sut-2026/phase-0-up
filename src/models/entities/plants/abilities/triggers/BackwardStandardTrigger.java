package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import java.util.List;

// The rear-facing half of a Split Pea. Same rule as the forward trigger, mirrored: a straight-firing
// backward barrel also fires at a grave that has appeared behind the plant.
public class BackwardStandardTrigger implements TriggerStrategy {
    private final boolean targetsObstacles;

    public BackwardStandardTrigger() {
        this(false);
    }

    public BackwardStandardTrigger(boolean targetsObstacles) {
        this.targetsObstacles = targetsObstacles;
    }

    @Override
    public boolean canTrigger(Plant owner, GameSession gameSession) {
        List<Zombie> zombiesInRow = gameSession.getMap().getRow(owner.getY()).getZombies();

        if (zombiesInRow != null) {
            for (Zombie z : zombiesInRow) {
                if (Targets.reachable(owner, z) && z.getMovement().getPositionX() < owner.getX()) {
                    return true;
                }
            }
        }
        return targetsObstacles && ObstacleSight.obstacleBehind(owner, gameSession);
    }
}
