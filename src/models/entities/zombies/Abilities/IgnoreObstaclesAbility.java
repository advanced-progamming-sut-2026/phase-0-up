package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Components.ActionState;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

public class IgnoreObstaclesAbility implements ZombieAbility {

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState().isUnableToMove()) {
            return;
        }
        zombie.getState().setFlying(true);
        Plant plantInFront = getPlantInFront(zombie);

        if (plantInFront != null && !plantInFront.isDead()) {
            if (plantInFront.getName().equalsIgnoreCase("Tall-nut")) {
                zombie.getState().setAction(ActionState.EATING);
            } else {
                if (zombie.getState().getCurrentAction() == ActionState.EATING) {
                    zombie.getState().setAction(ActionState.WALKING);
                }
            }
        }
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
            if (cell != null && cell.getCurrentPlant() != null) {
                if (Math.abs(zX - cell.getX()) <= 35.0) {
                    return cell.getCurrentPlant();
                }
            }
        }
        return null;
    }
}