package models.map;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;

import java.util.List;

public class Row {
    List<Cell> cells;
    int index;
    boolean hasZombie;
    List<Zombie> activeZombies;
    List<Projectiles> activeProjectiles;
    Lawnmower lawnmower;
    public Cell cellAt(int y){return null;}
    public void setLawnmower(){this.lawnmower.row = this.index;};
    public Plant frontPlant(){return null;}



}
