package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

// Turquoise (Crystal Skull): once its five-second sun heist is done it fires a powerful laser at the
// four tiles directly ahead of it in its own row, wiping out every plant standing there, then needs a
// cooldown before it can do the whole thing again (documents/project.md, Turquoise Zombie).
//
// The charge IS the steal: StealSunAbility raises the state's ready-for-laser flag when its five
// seconds are up, and this ability waits on that flag rather than running a second timer of its own --
// which is what the JSON's ChargingTime 5 / LaserCooldownTime 5 pair describes. Values mirror the JSON
// (LaserBeamDamage 4001, LaserCooldownTime 5s).
public class LaserBeamAbility implements ZombieAbility {
    private static final int TICKS_PER_SECOND = 10;
    private static final int COOLDOWN_TICKS = 5 * TICKS_PER_SECOND;
    private static final int LASER_DAMAGE = 4001;
    // "a powerful laser at the four tiles in front of it" -- the beam does not run the whole lane.
    private static final int LASER_REACH_TILES = 4;

    private int cooldownTimer = 0;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState().isUnableToMove()) {
            return;
        }
        if (cooldownTimer > 0) {
            cooldownTimer--;
            return;
        }
        // Not charged yet: the sun heist has not run its course, so there is nothing to fire.
        if (!zombie.getState().isReadyForLaser()) {
            return;
        }

        fireLaser(zombie);
        cooldownTimer = COOLDOWN_TICKS;
        // Clearing the flag both disarms the beam and tells StealSunAbility the shot is spent, so it can
        // line up the next heist.
        zombie.getState().setReadyForLaser(false);
    }

    private void fireLaser(Zombie zombie) {
        int row = zombie.getMovement().getPositionY();
        double zombieX = zombie.getMovement().getPositionX();
        Row r = zombie.getGameSession().getMap().getRow(row);
        if (r == null) {
            return;
        }
        double reachLimit = zombieX - LASER_REACH_TILES;
        for (Cell cell : r.getCells()) {
            Plant p = cell.getCurrentPlant();
            if (p != null && !p.isDead() && cell.getX() < zombieX && cell.getX() >= reachLimit
                    && p.getHealth() != null) {
                p.getHealth().takeDamage(LASER_DAMAGE);
            }
        }
        zombie.getGameSession().reportEvent(zombie.getAlias() + " fires a laser beam through the next "
                + LASER_REACH_TILES + " tiles of lane " + row + " from (" + (int) zombieX + ", "
                + row + ").");
    }
}
