package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import java.util.List;

// Fires when there is something worth shooting further down the plant's own lane: a targetable zombie,
// or -- for straight-firing plants only -- a grave still standing in the way (see GraveSight).
public class ForwardStandardTrigger implements TriggerStrategy {
    private final boolean targetsGraves;

    public ForwardStandardTrigger() {
        this(false);
    }

    public ForwardStandardTrigger(boolean targetsGraves) {
        this.targetsGraves = targetsGraves;
    }

    @Override
    public boolean canTrigger(Plant owner, GameSession gameSession) {
        List<Zombie> zombiesInRow = gameSession.getMap().getRow(owner.getY()).getZombies();

        if (zombiesInRow != null) {
            for (Zombie z : zombiesInRow) {
                if (z.isTargetable() && z.getMovement().getPositionX() > owner.getX()) {
                    return true;
                }
            }
        }
        return targetsGraves && GraveSight.graveAhead(owner, gameSession, 0.0);
    }
}
