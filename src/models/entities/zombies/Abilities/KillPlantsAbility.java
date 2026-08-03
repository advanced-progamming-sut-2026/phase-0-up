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
            extinguishTorch(zombie);
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

            String verb = requiresTorch ? " sets " : " smashes ";
            String tail = requiresTorch ? " ablaze at (" : " to pieces at (";
            zombie.getGameSession().reportEvent(zombie.getAlias() + verb + targetPlant.getName() + tail
                    + (int) targetPlant.getX() + ", " + targetPlant.getY() + ").");

            zombie.getState().setAction(ActionState.WALKING);
        }
        crushHypnotized(zombie);
    }

    // A hypnotized zombie is in the way exactly as a plant is, and a smash (or a torch) ends it the
    // same way -- the same rule ArcadePushAbility and PushIceAbility already apply. This is also how a
    // Gargantuar fights back at all: its EatDPS is 0, so it has no bite to trade in a duel.
    private void crushHypnotized(Zombie zombie) {
        Row row = zombie.getGameSession().getMap().getRow(zombie.getMovement().getPositionY());
        if (row == null) {
            return;
        }
        double zombieX = zombie.getMovement().getPositionX();
        for (Zombie other : new java.util.ArrayList<>(row.getZombies())) {
            if (other == zombie || !other.getState().isHypnotized() || other.getHealth().isDead()
                    || Math.abs(other.getMovement().getPositionX() - zombieX) > killThreshold) {
                continue;
            }
            other.getHealth().applyDamage(Integer.MAX_VALUE, null, null);
            zombie.getGameSession().reportEvent(zombie.getAlias()
                    + (requiresTorch ? " burns away your hypnotized " : " smashes your hypnotized ")
                    + other.getAlias() + ".");
        }
    }

    private Plant findTargetPlantInFront(Zombie zombie) {
        if (zombie.getMovement() == null || zombie.getGameSession() == null
                || zombie.getGameSession().getMap() == null) {
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

    public void extinguishTorch(Zombie zombie) {
        if (this.requiresTorch && this.isTorchLit) {
            this.isTorchLit = false;
            zombie.getGameSession().reportEvent(zombie.getAlias() + "'s torch is snuffed out by the ice at ("
                    + (int) zombie.getX() + ", " + zombie.getY() + ").");
        }
    }

    public void igniteTorch(Zombie zombie) {
        if (this.requiresTorch && !this.isTorchLit) {
            this.isTorchLit = true;
            zombie.getGameSession().reportEvent(zombie.getAlias() + "'s torch flares back to life at ("
                    + (int) zombie.getX() + ", " + zombie.getY() + ").");
        }
    }

    public boolean isTorchLit() {
        return isTorchLit;
    }
}