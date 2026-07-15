package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

public class ThrowIceAbility implements ZombieAbility {
    private int tickCounter = 0;
    private static final int TICKS_PER_SECOND = 10;
    private static final int THROW_COOLDOWN = 4 * TICKS_PER_SECOND;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState().isUnableToMove()) {
            return;
        }

        tickCounter++;
        if (tickCounter >= THROW_COOLDOWN) {
            Plant target = findClosestUnfrozenPlant(zombie);

            if (target != null) {
                target.takeIceHit();

                System.out.println(zombie.getAlias() + " threw ice at " + target.getName());
                tickCounter = 0;
            }
        }
    }


    private Plant findClosestUnfrozenPlant(Zombie zombie) {
        if (zombie.getMovement() == null || zombie.getGameSession() == null || zombie.getGameSession().getMap() == null) {
            return null;
        }

        int rowIdx = zombie.getMovement().getPositionY();
        double zX = zombie.getMovement().getPositionX();

        Row row = zombie.getGameSession().getMap().getRow(rowIdx);
        if (row == null || row.getCells() == null) {
            return null;
        }

        Plant closestPlant = null;
        double minDistance = Double.MAX_VALUE;

        for (Cell cell : row.getCells()) {
            if (cell != null && cell.getCurrentPlant() != null) {
                Plant plant = cell.getCurrentPlant();
                if (!plant.isDead() && !plant.isFrozen()) {
                    double distance = zX - cell.getX();
                    if (distance >= -0.2 && distance < minDistance) {
                        minDistance = distance;
                        closestPlant = plant;
                    }
                }
            }
        }

        return closestPlant;
    }
}