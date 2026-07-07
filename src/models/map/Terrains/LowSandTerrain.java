package models.map.Terrains;

import factories.ZombieFactory;
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
        if(flooded) ZombieFactory.createZombie("ZombieDefault" , currentCell.getX() , currentCell.getY());
    }
}
