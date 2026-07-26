package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

public class ThrowOctopusAbility implements ZombieAbility {
    private int tickCounter = 0;
    private static final int TICKS_PER_SECOND = 10;
    private static final int THROW_COOLDOWN = 8 * TICKS_PER_SECOND;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState().isUnableToMove()) {
            return;
        }

        tickCounter++;
        if (tickCounter >= THROW_COOLDOWN) {
            Plant target = findFrontmostFreePlant(zombie);

            if (target != null) {
                target.bindWithOctopus();

                zombie.getGameSession().reportEvent(zombie.getAlias() + " flings an octopus at "
                        + target.getName() + " at (" + (int) target.getX() + ", " + target.getY() + ").");
                tickCounter = 0;
            }
        }
    }

    private Plant findFrontmostFreePlant(Zombie zombie) {
        if (zombie.getMovement() == null || zombie.getGameSession() == null
                || zombie.getGameSession().getMap() == null) {
            return null;
        }

        int rowIdx = zombie.getMovement().getPositionY();
        Row row = zombie.getGameSession().getMap().getRow(rowIdx);
        if (row == null || row.getCells() == null) {
            return null;
        }

        for (int col = 8; col >= 0; col--) {
            Cell cell = row.cellAt(col);
            if (cell != null && cell.hasPlant()) {
                Plant plant = cell.getCurrentPlant();

                if (plant != null && !plant.isDead() && !plant.hasOctopus() && !plant.isFrozen()) {
                    return plant;
                }
            }
        }

        return null;
    }
}