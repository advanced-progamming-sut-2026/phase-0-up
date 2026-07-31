package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.plants.PlantTags;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

public class IgnoreObstaclesAbility implements ZombieAbility {

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState().isUnableToMove()) {
            return;
        }
        Plant plantInFront = getPlantInFront(zombie);

        // The flag stops EatPlantAbility chewing and ContactTrigger firing a mine underneath.
        zombie.getState().setFlying(plantInFront != null && fliesOver(plantInFront));
    }
    // The spec's obstacles: the WALL_NUT family, lane-shunting plants and mines. A Tall-nut stops it.
    private boolean fliesOver(Plant plant) {
        if (plant.getName() != null && plant.getName().equalsIgnoreCase("Tall-nut")) {
            return false;
        }
        if ("WALL_NUT".equalsIgnoreCase(plant.getCategory())) {
            return true;
        }
        return plant.getTags() != null && (plant.getTags().contains(PlantTags.MOVE_ZOMBIES)
                || plant.getTags().contains(PlantTags.TRAP));
    }

    private Plant getPlantInFront(Zombie zombie) {
        if (zombie.getMovement() == null || zombie.getGameSession() == null
                || zombie.getGameSession().getMap() == null) {
            return null;
        }

        int rowIdx = zombie.getMovement().getPositionY();
        double zX = zombie.getMovement().getPositionX();

        Row row = zombie.getGameSession().getMap().getRow(rowIdx);
        if (row == null || row.getCells() == null) {
            return null;
        }

        for (Cell cell : row.getCells()) {
            // Reach in TILES: the old 35.0 matched every plant in the row, not the one it reached.
            if (cell != null && cell.getDefendingPlant() != null && !cell.getDefendingPlant().isDead()) {
                if (Math.abs(zX - cell.getX()) <= 0.5) {
                    return cell.getDefendingPlant();
                }
            }
        }
        return null;
    }
}