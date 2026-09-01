package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import java.util.List;

// Fires when there is something worth shooting further down the plant's own lane: a targetable zombie,
// or -- for straight-firing plants only -- a grave still standing in the way (see ObstacleSight).
public class ForwardStandardTrigger implements TriggerStrategy {
    private final boolean targetsObstacles;

    public ForwardStandardTrigger() {
        this(false);
    }

    public ForwardStandardTrigger(boolean targetsObstacles) {
        this.targetsObstacles = targetsObstacles;
    }

    @Override
    public boolean canTrigger(Plant owner, GameSession gameSession) {
        List<Zombie> zombiesInRow = gameSession.getMap().getRow(owner.getY()).getZombies();

        if (zombiesInRow != null) {
            for (Zombie z : zombiesInRow) {
                if (Targets.reachable(owner, z) && z.getMovement().getPositionX() > owner.getX()) {
                    return true;
                }
            }
        }
        return targetsObstacles && ObstacleSight.obstacleAhead(owner, gameSession, 0.0);
    }
}
