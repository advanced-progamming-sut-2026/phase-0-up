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
                jester.getGameSession().reportEvent("The Jester Zombie whirls into a spin at ("
                        + (int) jester.getX() + ", " + jester.getY() + "), ready to deflect shots.");
            }
        } else {
            if (jester.getState().isSpinning()) {
                jester.getState().setSpinning(false);
                jester.getMovement().setSpeed(normalSpeed);
                jester.getGameSession().reportEvent("The Jester Zombie stops spinning at ("
                        + (int) jester.getX() + ", " + jester.getY() + ").");
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