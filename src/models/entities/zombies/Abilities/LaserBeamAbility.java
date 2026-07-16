package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

// Crystal Skull: charges for a few seconds, then fires a lane-long laser that devastates every plant
// ahead of it, followed by a cooldown before it can charge again. Values mirror the JSON
// (ChargingTime 5s, LaserBeamDamage 4001, LaserCooldownTime 5s).
public class LaserBeamAbility implements ZombieAbility {
    private static final int TICKS_PER_SECOND = 10;
    private static final int CHARGE_TICKS = 5 * TICKS_PER_SECOND;
    private static final int COOLDOWN_TICKS = 5 * TICKS_PER_SECOND;
    private static final int LASER_DAMAGE = 4001;

    private int chargeTimer = 0;
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

        chargeTimer++;
        zombie.getState().setReadyForLaser(chargeTimer >= CHARGE_TICKS);

        if (chargeTimer >= CHARGE_TICKS) {
            fireLaser(zombie);
            chargeTimer = 0;
            cooldownTimer = COOLDOWN_TICKS;
            zombie.getState().setReadyForLaser(false);
        }
    }

    private void fireLaser(Zombie zombie) {
        int row = zombie.getMovement().getPositionY();
        double zombieX = zombie.getMovement().getPositionX();
        Row r = zombie.getGameSession().getMap().getRow(row);
        if (r == null) {
            return;
        }
        for (Cell cell : r.getCells()) {
            Plant p = cell.getCurrentPlant();
            if (p != null && !p.isDead() && cell.getX() < zombieX && p.getHealth() != null) {
                p.getHealth().takeDamage(LASER_DAMAGE);
            }
        }
        System.out.println(zombie.getAlias() + " fired a laser beam down the lane!");
    }
}
