package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

// Arcade Zombie: shoves its arcade machine ahead, damaging the plant directly in front on a steady
// cadence (a battering ram rather than a nibble).
public class ArcadePushAbility implements ZombieAbility {
    private static final int TICKS_PER_SECOND = 10;
    private static final int PUSH_INTERVAL = TICKS_PER_SECOND;
    private static final int PUSH_DAMAGE = 200;
    private static final double PUSH_REACH = 1.0;

    private int timer = 0;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState().isUnableToMove()) {
            return;
        }
        timer++;
        if (timer < PUSH_INTERVAL) {
            return;
        }
        timer = 0;

        Plant front = frontPlant(zombie);
        if (front != null && front.getHealth() != null) {
            front.getHealth().takeDamage(PUSH_DAMAGE);
            System.out.println(zombie.getAlias() + " shoved its arcade machine into a plant!");
        }
    }

    private Plant frontPlant(Zombie zombie) {
        int row = zombie.getMovement().getPositionY();
        double zombieX = zombie.getMovement().getPositionX();
        Row r = zombie.getGameSession().getMap().getRow(row);
        if (r == null) {
            return null;
        }
        Plant closest = null;
        double minDistance = Double.MAX_VALUE;
        for (Cell cell : r.getCells()) {
            Plant p = cell.getCurrentPlant();
            if (p == null || p.isDead()) {
                continue;
            }
            double distance = zombieX - cell.getX(); // positive => plant is ahead toward the house
            if (distance >= 0 && distance <= PUSH_REACH && distance < minDistance) {
                minDistance = distance;
                closest = p;
            }
        }
        return closest;
    }
}
