package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.IceBlock;
import models.map.Row;

import java.util.ArrayList;
import java.util.List;

public class PushIceAbility implements ZombieAbility {
    private boolean hasIceBlocks = true;
    private static final double COLLISION_THRESHOLD = 0.4;

    @Override
    public void execute(Zombie troglobite) {
        if (troglobite == null || troglobite.getState().isUnableToMove() || !hasIceBlocks) {
            return;
        }

        if (!troglobite.getHealth().hasArmor()) {
            triggerIceDestroyed(troglobite);
            return;
        }
        crushObstaclesInFront(troglobite);
    }

    private void crushObstaclesInFront(Zombie troglobite) {
        if (troglobite.getGameSession() == null || troglobite.getGameSession().getMap() == null) {
            return;
        }

        int rowIdx = troglobite.getMovement().getPositionY();
        double zX = troglobite.getMovement().getPositionX();

        Row row = troglobite.getGameSession().getMap().getRow(rowIdx);
        if (row != null && row.getCells() != null) {
            for (Cell cell : row.getCells()) {
                if (cell != null && cell.getCurrentPlant() != null) {
                    Plant plant = cell.getCurrentPlant();
                    if (!plant.isDead()) {
                        double distance = Math.abs(zX - cell.getX());

                        if (distance <= COLLISION_THRESHOLD) {
                            if (plant.getHealth() != null) {
                                plant.getHealth().takeDamage(Integer.MAX_VALUE);
                            }
                            System.out.println("Troglobite's ice block crushed a plant: " + plant.getName());
                        }
                    }
                }
            }
        }
        List<Zombie> allZombies = new ArrayList<>();
        for(Row r : troglobite.getGameSession().getMap().getRows()){
            for(Zombie z : row.getZombies()){
                allZombies.add(z);
            }
        }

        if (allZombies != null) {
            for (Zombie otherZombie : allZombies) {
                if (otherZombie != troglobite &&
                        !otherZombie.getHealth().isDead() &&
                        otherZombie.getState().isHypnotized() &&
                        otherZombie.getMovement().getPositionY() == rowIdx) {

                    double distance = Math.abs(zX - otherZombie.getMovement().getPositionX());

                    if (distance <= COLLISION_THRESHOLD) {
                        otherZombie.getHealth().applyDamage(Integer.MAX_VALUE , null , null);
                        System.out.println("Troglobite's ice block crushed a hypnotized zombie!");
                    }
                }
            }
        }
    }

    private void triggerIceDestroyed(Zombie troglobite) {
        this.hasIceBlocks = false;
        System.out.println("All ice blocks destroyed! Troglobite is now walking and eating normally.");
    }

    public void onTroglobiteDeath(Zombie troglobite) {
        if (hasIceBlocks && troglobite.getGameSession() != null) {
            int row = troglobite.getMovement().getPositionY();
            double x = troglobite.getMovement().getPositionX();
            troglobite.getGameSession().getMap().getRow(row).addObstacle(
                    new IceBlock(troglobite.getHealth().getTotalHP() , x , row)
            );
            System.out.println("Troglobite died! Ice block left behind as an obstacle at X: " + x);
        }
    }

    public boolean hasIceBlocks() {
        return hasIceBlocks;
    }
}