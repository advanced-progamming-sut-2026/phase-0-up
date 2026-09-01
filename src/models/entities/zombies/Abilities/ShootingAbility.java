package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;
import utils.Constants;

// Zombotany Peashooter zombie: fires a pea straight ahead (toward the house) that strikes the nearest
// plant in its lane and damages it. Modelled as a periodic ranged hit rather than a travelling
// projectile, because the projectile system only carries plant-fired shots aimed at zombies.
//
// Because nothing is in flight, the shot is NARRATED -- and that sentence is the only record the view
// has to hang a pea on. Without it a Zombotany Peashooter chipped plants from four tiles away with
// nothing crossing the gap and nothing landing: plants losing health for no visible reason, which is
// indistinguishable from a bug. The same split every other reach-down-the-lane zombie ability in the
// game already uses -- see ThrowIceAbility and the Beach Zombie's octopus. ImpactEffects flies the pea.
public class ShootingAbility implements ZombieAbility {
    private static final int DEFAULT_DAMAGE = 20;
    private static final int DEFAULT_RELOAD_TICKS = (3 * Constants.TICKS_PER_SECOND) / 2;   // ~1.5s

    private final int damage;
    private final int reloadTicks;
    private int cooldown;

    public ShootingAbility() {
        this(DEFAULT_DAMAGE, DEFAULT_RELOAD_TICKS);
    }

    public ShootingAbility(int damage, int reloadTicks) {
        this.damage = damage;
        this.reloadTicks = Math.max(1, reloadTicks);
    }

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getHealth().isDead() || !zombie.isOnBoard()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        Plant target = nearestPlantAhead(zombie);
        if (target != null && target.getHealth() != null) {
            target.getHealth().takeDamage(damage);
            cooldown = reloadTicks;
            announce(zombie, target);
        }
    }

    // Both ends of the flight, because the view has neither: the zombie is at a continuous x that the
    // event has to round, and the plant's own tile is where the pea has to land.
    private void announce(Zombie zombie, Plant target) {
        if (zombie.getGameSession() == null) {
            return;
        }
        zombie.getGameSession().reportEvent(zombie.getAlias() + " spits a pea from ("
                + (int) zombie.getMovement().getPositionX() + ", "
                + zombie.getMovement().getPositionY() + ") at " + target.getName()
                + " at (" + (int) target.getX() + ", " + target.getY() + ").");
    }

    // The closest live plant ahead of the zombie (toward the house, i.e. at a lower column) in its lane.
    private Plant nearestPlantAhead(Zombie zombie) {
        if (zombie.getGameSession() == null || zombie.getGameSession().getMap() == null) {
            return null;
        }
        Row row = zombie.getGameSession().getMap().getRow(zombie.getMovement().getPositionY());
        if (row == null) {
            return null;
        }
        double zombieX = zombie.getMovement().getPositionX();
        Plant best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Cell cell : row.getCells()) {
            Plant plant = cell.getCurrentPlant();
            if (plant == null || plant.isDead()) {
                continue;
            }
            double distance = zombieX - cell.getX();   // ahead of the zombie => positive
            if (distance > 0 && distance < bestDistance) {
                bestDistance = distance;
                best = plant;
            }
        }
        return best;
    }
}
