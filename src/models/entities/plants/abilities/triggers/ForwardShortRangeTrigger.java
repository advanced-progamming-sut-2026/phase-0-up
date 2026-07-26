package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import java.util.List;

// As ForwardStandardTrigger, but only sees as far as the plant's shots actually travel. A straight
// firing short-range plant also opens up on a grave once the grave is inside that same reach.
public class ForwardShortRangeTrigger implements TriggerStrategy {
    private double range;
    private final boolean targetsGraves;

    public ForwardShortRangeTrigger(double range) {
        this(range, false);
    }

    public ForwardShortRangeTrigger(double range, boolean targetsGraves) {
        this.range = range;
        this.targetsGraves = targetsGraves;
    }

    @Override
    public boolean canTrigger(Plant owner, GameSession gameSession) {
        List<Zombie> zombiesInRow = gameSession.getMap().getRow(owner.getY()).getZombies();

        if (zombiesInRow != null) {
            double ownerX = owner.getX();

            for (Zombie z : zombiesInRow) {
                double zombieX = z.getMovement().getPositionX();
                if (z.isTargetable() && zombieX > ownerX && zombieX <= ownerX + range) {
                    return true;
                }
            }
        }
        return targetsGraves && GraveSight.graveAhead(owner, gameSession, range);
    }
}
