package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

// Arcade Zombie: its shoved machine kills a plant or hypnotized zombie "on the spot" -- every tick,
public class ArcadePushAbility implements ZombieAbility {
    private static final int PUSH_DAMAGE = Integer.MAX_VALUE;
    private static final double PUSH_REACH = 1.0;


    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState().isUnableToMove()) {
            return;
        }

        Plant front = frontPlant(zombie);
        if (front != null && front.getHealth() != null) {
            front.getHealth().takeDamage(PUSH_DAMAGE);
            zombie.getGameSession().reportEvent(zombie.getAlias() + " rams its arcade machine into "
                    + front.getName() + " at (" + (int) front.getX() + ", " + front.getY() + ").");
        }
        crushHypnotizedInFront(zombie);
    }
    // A hypnotized zombie is in the machine's way exactly as a plant is, and meets the same end.
    private void crushHypnotizedInFront(Zombie zombie) {
        Row row = zombie.getGameSession().getMap().getRow(zombie.getMovement().getPositionY());
        if (row == null) {
            return;
        }
        double zombieX = zombie.getMovement().getPositionX();
        for (Zombie other : new java.util.ArrayList<>(row.getZombies())) {
            if (other != zombie && other.getState().isHypnotized() && !other.getHealth().isDead()
                    && Math.abs(other.getMovement().getPositionX() - zombieX) <= PUSH_REACH) {
                other.getHealth().applyDamage(PUSH_DAMAGE, null, null);
                zombie.getGameSession().reportEvent(zombie.getAlias()
                        + " runs its arcade machine over a hypnotized " + other.getAlias() + ".");
            }
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
