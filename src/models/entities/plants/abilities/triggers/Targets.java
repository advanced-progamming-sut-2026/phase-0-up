package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;

// Whether a particular PLANT has any business shooting at a particular zombie.
//
// Every trigger used to ask Zombie.isTargetable() on its own, which answers "is this zombie a legal
// target for anything at all" -- alive, on the board, not charmed. That is the right question for a
// blast or a melee swing and the wrong one for a shooter, because it leaves out the one case where the
// answer depends on who is asking.
//
// ## The Snorkel Zombie
//
// It swims up the lane submerged, and Projectile.onHit has always let a straight shot pass harmlessly
// through it -- only a LOBBED shot comes down on top of one. The triggers did not know that, so every
// Peashooter on the board opened fire on a snorkeler and emptied itself into the water: peas flying,
// nothing happening, and the plant's own recharge spent. It looked exactly like a broken plant.
//
// So the question a trigger asks is now "can MY shots reach it", and for a submerged zombie only a
// lobber says yes. Everything else about targetability is unchanged and still asked of the zombie.
public final class Targets {

    private Targets() { }

    public static boolean reachable(Plant owner, Zombie zombie) {
        if (zombie == null || !zombie.isTargetable()) {
            return false;
        }
        if (!zombie.getState().isSubmerged()) {
            return true;
        }
        return owner != null && owner.lobsShots();
    }
}
