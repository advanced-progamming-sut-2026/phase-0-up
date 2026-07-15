package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

public class FishThePlants implements ZombieAbility {
    private int tickCounter = 0;
    private static final int TICKS_PER_SECOND = 10;
    private static final int FISHING_COOLDOWN = 5 * TICKS_PER_SECOND;

    @Override
    public void execute(Zombie fisherman) {
        if (fisherman == null || fisherman.getState().isUnableToMove()) {
            return;
        }
        tickCounter++;
        if (tickCounter >= FISHING_COOLDOWN) {
            boolean success = tryFishPlant(fisherman);
            if (success) {
                tickCounter = 0;
            }
        }
    }

    private boolean tryFishPlant(Zombie fisherman) {
        if (fisherman.getGameSession() == null || fisherman.getGameSession().getMap() == null) {
            return false;
        }

        int rowIdx = fisherman.getMovement().getPositionY();
        Row row = fisherman.getGameSession().getMap().getRow(rowIdx);
        if (row == null || row.getCells() == null) {
            return false;
        }
        for (int col = 7; col >= 0; col--) {
            Cell currentCell = row.cellAt(col);

            if (currentCell != null && currentCell.hasPlant()) {
                Plant targetPlant = currentCell.getCurrentPlant();

                if (targetPlant != null && !targetPlant.isDead()) {
                    if (col == 7) {
                        destroyHookedPlant(currentCell, targetPlant);
                        return true;
                    }

                    Cell rightCell = row.cellAt(col + 1);
                    if (rightCell != null && !rightCell.hasPlant()) {
                        movePlantToRight(currentCell, rightCell, targetPlant);
                        return true;
                    }
                    return false;
                }
            }
        }

        return false;
    }

    private void destroyHookedPlant(Cell cell, Plant plant) {
        System.out.println("Fisherman Zombie hooked " + plant.getName() + " and threw it into the ocean!");

        if (plant.getHealth() != null) {
            plant.getHealth().takeDamage(Integer.MAX_VALUE);
        }

        cell.removePlant();
    }

    private void movePlantToRight(Cell fromCell, Cell toCell, Plant plant) {
        System.out.println("Fisherman Zombie pulled " + plant.getName() + " one tile to the right!");
        fromCell.removePlant();

        toCell.addPlant(plant);
    }
}