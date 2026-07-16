package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

// Modern All-Star: sprints down a clear lane and tackle-smashes the first plant it reaches for heavy
// damage (SmashDamage 1500), dropping back to normal speed while a plant is in reach.
public class FootballTackleAbility implements ZombieAbility {
    private static final int SMASH_DAMAGE = 1500;
    private static final double TACKLE_REACH = 0.6;
    private static final double SPRINT_MULTIPLIER = 1.5;

    private boolean sprinting = false;
    private double baseSpeed = -1.0;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState().isUnableToMove()) {
            return;
        }
        if (baseSpeed < 0) {
            baseSpeed = zombie.getMovement().getSpeed();
        }

        Plant front = frontPlant(zombie);
        if (front != null && front.getHealth() != null) {
            front.getHealth().takeDamage(SMASH_DAMAGE);
            System.out.println(zombie.getAlias() + " tackled a plant!");
        }

        // sprint only while the lane ahead is clear
        boolean shouldSprint = (front == null);
        if (shouldSprint != sprinting) {
            sprinting = shouldSprint;
            zombie.getMovement().setSpeed(sprinting ? baseSpeed * SPRINT_MULTIPLIER : baseSpeed);
        }
    }

    private Plant frontPlant(Zombie zombie) {
        int row = zombie.getMovement().getPositionY();
        double zombieX = zombie.getMovement().getPositionX();
        Row r = zombie.getGameSession().getMap().getRow(row);
        if (r == null) {
            return null;
        }
        for (Cell cell : r.getCells()) {
            Plant p = cell.getCurrentPlant();
            if (p != null && !p.isDead()) {
                double distance = zombieX - cell.getX();
                if (distance >= 0 && distance <= TACKLE_REACH) {
                    return p;
                }
            }
        }
        return null;
    }
}
