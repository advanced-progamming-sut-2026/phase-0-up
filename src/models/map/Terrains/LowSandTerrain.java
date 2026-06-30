package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;

public class LowSandTerrain extends Terrain{
    private boolean flooded;
    private Cell currentCell;

    public LowSandTerrain(Cell currentCell) {
        this.currentCell = currentCell;
        this.plantable = true;
        this.symbol = '-';
        if(currentCell.isFlooded()) flooded = true;
        else flooded = false;
    }

    @Override
    public void effect(Zombie z, Plant p) {
        z.getMovement().move(z.getState() , currentCell.getX() , currentCell.getY());
    }
}
