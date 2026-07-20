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
                            troglobite.getGameSession().reportEvent("The Troglobite's ice block crushes "
                                    + plant.getName() + " at (" + (int) cell.getX() + ", " + rowIdx + ").");
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
                        troglobite.getGameSession().reportEvent("The Troglobite's ice block crushes a "
                                + "hypnotized zombie at (" + (int) otherZombie.getX() + ", " + rowIdx + ").");
                    }
                }
            }
        }
    }

    private void triggerIceDestroyed(Zombie troglobite) {
        this.hasIceBlocks = false;
        troglobite.getGameSession().reportEvent("The Troglobite's ice blocks are gone at ("
                + (int) troglobite.getX() + ", " + troglobite.getY() + "); it now walks and eats normally.");
    }

    public void onTroglobiteDeath(Zombie troglobite) {
        if (hasIceBlocks && troglobite.getGameSession() != null) {
            int row = troglobite.getMovement().getPositionY();
            double x = troglobite.getMovement().getPositionX();
            troglobite.getGameSession().getMap().getRow(row).addObstacle(
                    new IceBlock(troglobite.getHealth().getTotalHP() , x , row, troglobite.getGameSession())
            );
            troglobite.getGameSession().reportEvent("The Troglobite falls at (" + (int) x + ", " + row
                    + "), leaving its ice block behind as an obstacle.");
        }
    }

    public boolean hasIceBlocks() {
        return hasIceBlocks;
    }
}