package models.map.Terrains;

import factories.ZombieFactory;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

public class LowSandTerrain extends Terrain{
    private boolean flooded;
    private Cell currentCell;
    private Row currentRow;

    public LowSandTerrain(Row currentRow , int cellX) {
        this.plantable = true;
        this.symbol = '-';
        this.currentRow = currentRow;
        currentCell = currentRow.cellAt(cellX);
        if(currentCell.isFlooded()) flooded = true;
        else flooded = false;
    }

    @Override
    public void effect(Zombie z, Plant p) {
        Zombie zombie = null;
        if(flooded && z != null) {
            zombie = ZombieFactory.createZombie(
                    "ZombieDefault", currentCell.getX(), currentCell.getY(), z.getGameSession());
        }
        currentRow.getZombies().add(zombie);
    }
}
