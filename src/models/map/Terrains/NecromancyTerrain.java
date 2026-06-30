package models.map.Terrains;

import factories.ZombieFactory;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Cell;

public class NecromancyTerrain extends Terrain{
    private GameSession gameSession;
    private boolean hasGrave;
    private Cell cell;

    public NecromancyTerrain(GameSession gameSession , Cell cell) {
        this.gameSession = gameSession;
        this.cell = cell;
        this.hasGrave = false;
        this.plantable = true;
        this.symbol = '0';
    }

    public void setHasGrave(boolean hasGrave) {
        this.hasGrave = hasGrave;
        if(hasGrave) this.plantable = false;
        else this.plantable = true;
    }

    public Zombie spawnFromGrave(){
        return ZombieFactory.createZombie("ZombieDefault" , cell.getX() , cell.getY());
    }
    @Override
    public void effect(Zombie z, Plant p) {
    }
}
