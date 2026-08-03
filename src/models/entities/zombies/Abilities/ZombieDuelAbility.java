package models.entities.zombies.Abilities;

import models.entities.zombies.Components.ActionState;
import models.entities.zombies.Zombie;
import models.map.Row;

// Zombie-versus-zombie combat. A hypnotized zombie fights for the player, so when it meets an ordinary
// zombie in its lane the two stop and brawl until one of them falls (documents/project.md refers to
// hypnotized zombies destroying things alongside the plants).
//
// Every zombie carries this ability; it stays dormant until the zombie it has walked into is on the
// other side. The two directions are deliberately asymmetric:
//   * a HYPNOTIZED zombie always attacks -- picking fights is the whole point of turning it;
//   * an ORDINARY zombie only turns on it when it has no plant to eat, so a zombie already chewing a
//     Wall-nut keeps chewing rather than getting a free second attack each tick.
//
// Damage and cadence are the zombie's own bite values, so a Gargantuar hits like a Gargantuar.
public class ZombieDuelAbility implements ZombieAbility {
    private int tickCounter = 0;
    // Mirrors EatPlantAbility: fallback cadence when a zombie's eatSpeed was never set.
    private static final int DEFAULT_TICKS_PER_ATTACK = 10;
    // Contact reach in tiles. Slightly wider than the plant-eating threshold because both fighters may
    // be moving toward each other and can close the gap by more than one step in a tick.
    private static final double COLLISION_THRESHOLD = 0.4;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState().isFrozen() || zombie.getState().isButtered()
                || zombie.getState().getCurrentAction() == ActionState.DYING) {
            return;
        }

        Zombie enemy = findEnemy(zombie);
        if (enemy == null) {
            // Only a hypnotized zombie's EATING can have come from a duel -- an ordinary zombie's comes
            // from EatPlantAbility, and clearing it here would interrupt its meal.
            if (zombie.getState().isHypnotized()
                    && zombie.getState().getCurrentAction() == ActionState.EATING) {
                zombie.getState().setAction(ActionState.WALKING);
            }
            tickCounter = 0;
            return;
        }

        zombie.getState().setAction(ActionState.EATING);
        tickCounter++;
        int baseTicks = zombie.getEatSpeed() > 0 ? zombie.getEatSpeed() : DEFAULT_TICKS_PER_ATTACK;
        int requiredTicks = zombie.getState().isChilled() ? (baseTicks * 2) : baseTicks;
        if (tickCounter < requiredTicks) {
            return;
        }
        tickCounter = 0;

        enemy.getHealth().applyDamage(zombie.getEatDamage(), null, null);
        if (enemy.getHealth().isDead() && zombie.getGameSession() != null) {
            zombie.getGameSession().reportEvent(zombie.getState().isHypnotized()
                    ? "Your hypnotized " + zombie.getAlias() + " tears down a " + enemy.getAlias() + "."
                    : "A " + zombie.getAlias() + " turns on your hypnotized " + enemy.getAlias()
                            + " and drops it.");
        }
    }

    // The nearest zombie in the same lane, within contact range, standing on the other side of the
    // fight. Returns null when there is nobody to swing at.
    private Zombie findEnemy(Zombie zombie) {
        if (zombie.getGameSession() == null || zombie.getGameSession().getMap() == null
                || zombie.getMovement() == null) {
            return null;
        }
        // A zombie with no bite at all (the Gargantuar, EatDPS 0) kills by smashing instead -- see
        // KillPlantsAbility. Engaging here would only pin it in EATING while dealing 0 damage, which
        // deadlocked it against a hypnotized zombie neither side could finish.
        if (zombie.getEatDamage() <= 0) {
            return null;
        }
        // An ordinary zombie with a plant in front of it is busy; it does not also start a brawl.
        if (!zombie.getState().isHypnotized() && zombie.getTargetPlantInFront() != null) {
            return null;
        }

        Row row = zombie.getGameSession().getMap().getRow(zombie.getMovement().getPositionY());
        if (row == null) {
            return null;
        }
        double zombieX = zombie.getMovement().getPositionX();
        Zombie closest = null;
        double minDistance = Double.MAX_VALUE;
        for (Zombie other : row.getZombies()) {
            if (other == zombie || other.getHealth().isDead()
                    || other.getState().isHypnotized() == zombie.getState().isHypnotized()) {
                continue;
            }
            double distance = Math.abs(zombieX - other.getMovement().getPositionX());
            if (distance <= COLLISION_THRESHOLD && distance < minDistance) {
                minDistance = distance;
                closest = other;
            }
        }
        return closest;
    }
}
