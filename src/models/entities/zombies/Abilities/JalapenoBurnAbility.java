package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;
import utils.Constants;

// Zombotany Jalapeno zombie: if it survives about 10 seconds after entering the garden, it ignites its
// whole lane -- every plant in the row is destroyed and the zombie is consumed in the blast. Killing it
// before the fuse runs out prevents the burn (a dead zombie never ticks its abilities).
//
// ## The burn was already happening. Nothing said so.
//
// The rule below has always worked: the fuse burned, the row's plants died, the zombie killed itself.
// It raised no event of any kind, so the whole thing was invisible -- an entire lane of plants blinking
// out at once, with no fire, no bang, no warning that a clock had ever been running, and the zombie
// responsible quietly falling over as an ordinary corpse. From the player's side that is not an ability,
// it is a lane of plants vanishing for no reason, which is indistinguishable from a bug.
//
// So this now narrates both ends of the fuse, and neither sentence is decoration:
//
//   * The DETONATION is spelled exactly as a Jalapeno plant's own -- "Jalapeno detonates at (c, r)!" --
//     because that is the sentence ExplosionEffects already answers with a full lane of JALAPENO_FIRE,
//     the game's own row burn. The thing that detonated IS a Jalapeno; that it is riding a zombie's
//     shoulders does not make it a different explosion, and giving it a sentence of its own would mean
//     a second copy of the lane-fire code to draw the same fire.
//     It also makes DeathEffects.killedByBlast answer yes for this zombie, so the one zombie in the game
//     that immolates itself is reduced to ash rather than toppling over intact.
//
//   * The WARNING, two seconds out. The player is being asked to kill this zombie inside ten seconds and
//     nothing on the board says which zombie or that anything is counting -- a race nobody is told they
//     have entered is just an ambush. Late enough to be a warning rather than a running commentary, and
//     early enough to still be worth acting on.
public class JalapenoBurnAbility implements ZombieAbility {
    private static final int FUSE_TICKS = 10 * Constants.TICKS_PER_SECOND;

    // How long before the blast to shout about it.
    private static final int WARNING_TICKS = 2 * Constants.TICKS_PER_SECOND;

    // The name the view keys the lane fire off. Matched case-insensitively against
    // ExplosionEffects.JALAPENO_NAME, and the two must agree: any other spelling gets the default 3x3
    // blast art instead of the row burn.
    private static final String JALAPENO = "Jalapeno";

    private int onBoardTicks;
    private boolean detonated;
    private boolean warned;

    @Override
    public void execute(Zombie zombie) {
        if (detonated || zombie == null || zombie.getHealth().isDead() || !zombie.isOnBoard()) {
            return;   // the fuse only burns while the zombie is alive and on the lawn
        }
        onBoardTicks++;
        if (onBoardTicks >= FUSE_TICKS - WARNING_TICKS && !warned) {
            warned = true;
            report(zombie, "The Jalapeno Zombie in lane " + zombie.getMovement().getPositionY()
                    + " is about to blow -- take it down!");
        }
        if (onBoardTicks < FUSE_TICKS) {
            return;
        }
        detonated = true;
        Row row = zombie.getGameSession().getMap().getRow(zombie.getMovement().getPositionY());
        if (row != null) {
            for (Cell cell : row.getCells()) {
                destroy(cell.getCurrentPlant());
                destroy(cell.getProtector());
            }
        }
        // Announced BEFORE the zombie is killed, so the blast is on the view's books by the time the
        // death from it arrives -- DeathEffects settles corpse-or-ash a frame later and asks the
        // explosions whether one of them did it. See the note at the top of DeathEffects about the two
        // event queues.
        report(zombie, JALAPENO + " detonates at ("
                + (int) zombie.getMovement().getPositionX() + ", "
                + zombie.getMovement().getPositionY() + ")!");
        zombie.getHealth().applyDamage(zombie.getHealth().getTotalHP(), Element.FIRE, null);
    }

    private void report(Zombie zombie, String message) {
        if (zombie.getGameSession() != null) {
            zombie.getGameSession().reportEvent(message);
        }
    }

    private void destroy(Plant plant) {
        if (plant != null && !plant.isDead() && plant.getHealth() != null) {
            plant.getHealth().takeDamage(Integer.MAX_VALUE);
        }
    }
}
