package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Components.ActionState;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

public class KillPlantsAbility implements ZombieAbility {
    private boolean isTorchLit = true;
    private final boolean requiresTorch;
    private final double killThreshold;

    public KillPlantsAbility(boolean requiresTorch, double killThreshold) {
        this.requiresTorch = requiresTorch;
        this.killThreshold = killThreshold;
    }

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState().isUnableToMove()) {
            return;
        }

        if (requiresTorch && !isTorchLit) {
            return;
        }

        if (requiresTorch && (zombie.getState().isChilled() || zombie.getState().isFrozen())) {
            extinguishTorch();
            return;
        }

        Plant targetPlant = findTargetPlantInFront(zombie);
        if (targetPlant != null && !targetPlant.isDead()) {
            if (zombie.getState().getCurrentAction() != ActionState.EATING) {
                zombie.getState().setAction(ActionState.EATING);
            }

            if (targetPlant.getHealth() != null) {
                targetPlant.getHealth().takeDamage(Integer.MAX_VALUE);
            }

            System.out.println(zombie.getAlias() + " killed a plant instantly!");

            zombie.getState().setAction(ActionState.WALKING);
        }
    }

    private Plant findTargetPlantInFront(Zombie zombie) {
        if (zombie.getMovement() == null || zombie.getGameSession() == null || zombie.getGameSession().getMap() == null) {
            return null;
        }

        int zombieRow = zombie.getMovement().getPositionY();
        double zombieX = zombie.getMovement().getPositionX();

        Row row = zombie.getGameSession().getMap().getRow(zombieRow);
        if (row == null || row.getCells() == null) {
            return null;
        }

        Plant closestPlant = null;
        double minDistance = Double.MAX_VALUE;

        for (Cell cell : row.getCells()) {
            if (cell != null) {
                Plant plant = cell.getCurrentPlant();
                if (plant != null && !plant.isDead()) {
                    double distance = Math.abs(zombieX - cell.getX());

                    if (distance <= killThreshold && distance < minDistance) {
                        minDistance = distance;
                        closestPlant = plant;
                    }
                }
            }
        }

        return closestPlant;
    }

    public void extinguishTorch() {
        if (this.requiresTorch && this.isTorchLit) {
            this.isTorchLit = false;
            System.out.println("Torch was extinguished by ice!");
        }
    }

    public void igniteTorch() {
        if (this.requiresTorch && !this.isTorchLit) {
            this.isTorchLit = true;
            System.out.println("Torch was reignited by fire!");
        }
    }

    public boolean isTorchLit() {
        return isTorchLit;
    }
}