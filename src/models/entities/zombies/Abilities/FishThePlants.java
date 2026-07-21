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
                        destroyHookedPlant(fisherman, currentCell, targetPlant);
                        return true;
                    }

                    Cell rightCell = row.cellAt(col + 1);
                    if (rightCell != null && !rightCell.hasPlant()) {
                        movePlantToRight(fisherman, currentCell, rightCell, targetPlant);
                        return true;
                    }
                    return false;
                }
            }
        }

        return false;
    }

    private void destroyHookedPlant(Zombie fisherman, Cell cell, Plant plant) {
        fisherman.getGameSession().reportEvent("The Fisherman Zombie hooks " + plant.getName()
                + " at (" + (int) cell.getX() + ", " + cell.getY() + ") and drags it into the ocean.");

        if (plant.getHealth() != null) {
            plant.getHealth().takeDamage(Integer.MAX_VALUE);
        }

        cell.removePlant();
    }

    private void movePlantToRight(Zombie fisherman, Cell fromCell, Cell toCell, Plant plant) {
        fromCell.removePlant();

        // The destination decides the plant's fate. Dragging a land plant onto open water with no Lily
        // Pad under it drowns it -- Cell.addPlant refuses that placement, and previously the plant was
        // simply dropped on the floor: gone from the board with no death, no event and no plants-lost
        // tally (so quests undercounted). Kill it properly instead, and only announce a move that
        // actually happened.
        if (!toCell.addPlant(plant).success()) {
            plant.getHealth().takeDamage(Integer.MAX_VALUE);
            fisherman.getGameSession().reportEvent("The Fisherman Zombie drags " + plant.getName()
                    + " off (" + (int) fromCell.getX() + ", " + fromCell.getY()
                    + ") and it drowns in the water.");
            fisherman.getGameSession().recordPlantLost();
            return;
        }
        fisherman.getGameSession().reportEvent("The Fisherman Zombie reels " + plant.getName()
                + " one tile to the right, to (" + (int) toCell.getX() + ", " + toCell.getY() + ").");
    }
}