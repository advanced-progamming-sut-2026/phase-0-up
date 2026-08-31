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
            // A zombie in the air sets off no mine -- the Dodo Rider flies over them.
            //
            // Both halves are needed. isFlying() is the flag the zombie pass sets, and it is a tick
            // behind here: this trigger runs in CombatSystem's PLANT pass, before the zombies have
            // moved or re-decided anything, so on the tick a rider first reaches a mine the flag still
            // says it is walking. Asking the rule directly covers exactly that tick.
            if (z.getState().isFlying()
                    || models.entities.zombies.Abilities.IgnoreObstaclesAbility.fliesOver(z, owner)) {
                continue;
            }
            if (z.isTargetable()
                    && Math.abs(z.getMovement().getPositionX() - owner.getX()) <= 0.5) {
                return true;
            }
        }
        return false;
    }
}
