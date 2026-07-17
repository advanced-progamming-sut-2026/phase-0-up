package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;

import java.util.List;

// Fires when a live zombie stands on the plant's own tile (traps/mines).
public class ContactTrigger implements TriggerStrategy {
    @Override
    public boolean canTrigger(Plant owner, GameSession gameSession) {
        List<Zombie> zombies = gameSession.getMap().getRow(owner.getY()).getZombies();
        if (zombies == null) return false;

        for (Zombie z : zombies) {
            if (z.isTargetable()
                    && Math.abs(z.getMovement().getPositionX() - owner.getX()) <= 0.5) {
                return true;
            }
        }
        return false;
    }
}
