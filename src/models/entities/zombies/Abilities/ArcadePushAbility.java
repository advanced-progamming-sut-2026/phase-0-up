package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

// Arcade Zombie: its shoved machine kills a plant or hypnotized zombie "on the spot".
//
// The machine has HP of its own -- "as much as a Buckethead", says the spec -- so it is an armour layer
// on the zombie (ARCADE_CABINET, added by ZombieJSONParser), the same arrangement the Troglobite's ice
// blocks use. That is what gives the cabinet somewhere to live: damage peels into it first, and when it
// is gone the zombie stops shoving and walks and eats like anything else.
//
// Before this it had no end at all. The machine could not be destroyed, so an Arcade Zombie flattened
// every plant in its lane from the moment it walked on to the moment it reached the house, and neither
// the `push` clip nor the cabinet's own art was ever asked for.
public class ArcadePushAbility implements ZombieAbility {
    private static final int PUSH_DAMAGE = Integer.MAX_VALUE;
    private static final double PUSH_REACH = 1.0;

    // Whether this zombie still has its machine in front of it. Read by the view to decide between the
    // `push` and `walk` clips, and to draw the cabinet at all.
    public static boolean stillPushing(Zombie zombie) {
        if (zombie == null || zombie.getHealth() == null) {
            return false;
        }
        for (models.entities.zombies.Components.HealthLayer layer : zombie.getHealth().getLayers()) {
            if (layer.getType() == models.entities.zombies.Components.ArmorType.ARCADE_CABINET) {
                return true;
            }
        }
        return false;
    }

    // Reported once, on the tick the machine goes, so the view can play its break animation.
    private boolean announcedBreak;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null) {
            return;
        }
        if (!stillPushing(zombie)) {
            if (!announcedBreak) {
                announcedBreak = true;
                zombie.getGameSession().reportEvent(zombie.getAlias()
                        + "'s arcade machine falls apart at (" + (int) zombie.getX() + ", "
                        + zombie.getY() + "); it walks and eats normally now.");
            }
            return;
        }
        if (zombie.getState().isUnableToMove()) {
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
