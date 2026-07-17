package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import java.util.List;

public class ForwardStandardTrigger implements TriggerStrategy {

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
        return false;
    }
}
