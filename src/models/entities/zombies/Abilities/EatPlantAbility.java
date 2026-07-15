package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Components.ActionState;
import models.entities.zombies.Zombie;
import models.map.Cell;

public class EatPlantAbility implements ZombieAbility {
    private int tickCounter = 0;
    private static final int TICKS_PER_ATTACK = 3;

    private static final double COLLISION_THRESHOLD = 0.3;

    @Override
    public void execute(Zombie zombie) {
        if (zombie.getState().isUnableToMove()) {
            return;
        }

        Plant targetPlant = findTargetPlant(zombie);

        if (targetPlant == null || targetPlant.isDead() && !targetPlant.isCat()) {
            if (zombie.getState().getCurrentAction() == ActionState.EATING) {
                zombie.getState().setAction(ActionState.WALKING);
            }
            tickCounter = 0;
            return;
        }
        zombie.getState().setAction(ActionState.EATING);

        tickCounter++;
        int requiredTicks = zombie.getState().isChilled() ? (TICKS_PER_ATTACK * 2) : TICKS_PER_ATTACK;

        if (tickCounter >= requiredTicks) {
            if (targetPlant.getHealth() != null) {
                targetPlant.getHealth().takeDamage(zombie.getEatDamage());
            }
            targetPlant.onEaten(zombie, zombie.getGameSession());

            tickCounter = 0;
        }
    }

    private Plant findTargetPlant(Zombie zombie) {
        int zombieRow = zombie.getMovement().getPositionY();
        double zombieX = zombie.getMovement().getPositionX();

        Plant closestPlant = null;
        double minDistance = Double.MAX_VALUE;

        for (Cell cell : zombie.getGameSession().getMap().getRow(zombieRow).getCells()) {
            Plant plant = cell.getDefendingPlant(); // eats the protective cover (Pumpkin) first, if any
            if (plant != null && plant.getY() == zombieRow && !plant.isDead()) {
                double distance = Math.abs(zombieX - plant.getX());

                if (distance <= COLLISION_THRESHOLD) {
                    if (distance < minDistance) {
                        minDistance = distance;
                        closestPlant = plant;
                    }
                }
            }
        }

        return closestPlant;
    }
}