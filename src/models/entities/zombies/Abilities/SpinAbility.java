package models.entities.zombies.Abilities;

import models.entities.projectiles.Projectile;
import models.entities.projectiles.Trajectory;
import models.entities.zombies.Zombie;
import models.map.Row;

public class SpinAbility implements ZombieAbility {
    private double normalSpeed = -1.0;
    private static final double SPIN_SPEED_MULTIPLIER = 2.0;
    private static final double DETECTION_DISTANCE = 1.5;

    @Override
    public void execute(Zombie jester) {
        if (jester == null || jester.getState().isUnableToMove() || jester.getMovement() == null) {
            return;
        }

        if (normalSpeed < 0) {
            normalSpeed = jester.getMovement().getSpeed();
        }

        boolean incomingProjectile = hasIncomingDirectProjectile(jester);

        if (incomingProjectile) {
            if (!jester.getState().isSpinning()) {
                jester.getState().setSpinning(true);
                jester.getMovement().setSpeed(normalSpeed * SPIN_SPEED_MULTIPLIER);
                System.out.println("Jester Zombie started spinning and speeding up!");
            }
        } else {
            if (jester.getState().isSpinning()) {
                jester.getState().setSpinning(false);
                jester.getMovement().setSpeed(normalSpeed);
                System.out.println("Jester Zombie stopped spinning and returned to normal speed.");
            }
        }
    }

    private boolean hasIncomingDirectProjectile(Zombie jester) {
        if (jester.getGameSession() == null || jester.getGameSession().getMap() == null) {
            return false;
        }

        int rowIdx = jester.getMovement().getPositionY();
        Row row = jester.getGameSession().getMap().getRow(rowIdx);
        if (row == null || row.getActiveProjectiles() == null) {
            return false;
        }

        double zX = jester.getMovement().getPositionX();

        for (Projectile p : row.getActiveProjectiles()) {
            if (!p.isDestroyed() && p.getTrajectory() != Trajectory.LOBBED) {
                if (p.getX() < zX && (zX - p.getX()) <= DETECTION_DISTANCE) {
                    return true;
                }
            }
        }

        return false;
    }
}