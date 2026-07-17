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

    // A low beach only lets zombies surface while water is actually on it, and the tile floods/drains
    // during the level -- so the flooded state is read live rather than from construction time.
    @Override
    public void effect(Zombie z, Plant p) {
        flooded = currentCell.isFlooded();
        if (!flooded || z == null) {
            return;
        }
        Zombie zombie = ZombieFactory.createZombie(
                "ZombieDefault", currentCell.getX(), currentCell.getY(), z.getGameSession());
        if (zombie != null) {
            // Guarded: adding an un-created zombie put a null into the row and NPE'd every later pass.
            currentRow.getZombies().add(zombie);
        }
    }
}
