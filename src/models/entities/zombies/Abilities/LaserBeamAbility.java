package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

// Turquoise: its five-second sun heist IS the charge, then it lasers the four tiles ahead.
public class LaserBeamAbility implements ZombieAbility {
    private static final int TICKS_PER_SECOND = 10;
    private static final int COOLDOWN_TICKS = 5 * TICKS_PER_SECOND;
    private static final int LASER_DAMAGE = 4001;
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
        if (!zombie.getState().isReadyForLaser()) {
            return;
        }
        fireLaser(zombie);
        cooldownTimer = COOLDOWN_TICKS;
        // Clearing the flag disarms the beam and lets StealSunAbility line up the next heist.
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
