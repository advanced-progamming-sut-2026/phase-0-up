package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import java.util.List;

public class ForwardShortRangeTrigger implements TriggerStrategy {
    private double range;

    public ForwardShortRangeTrigger(double range) {
        this.range = range;
    }

    @Override
    public boolean canTrigger(Plant owner, GameSession gameSession) {
        List<Zombie> zombiesInRow = gameSession.getMap().getRow(owner.getY()).getZombies();

        if (zombiesInRow != null) {
            double ownerX = owner.getX();

            for (Zombie z : zombiesInRow) {
                double zombieX = z.getMovement().getPositionX();
                if (!z.getHealth().isDead() && zombieX > ownerX && zombieX <= ownerX + range) {
                    return true;
                }
            }
        }
        return false;
    }
}
