package models.map;

import models.entities.plants.Plant;
import models.entities.projectiles.Projectile;
import models.entities.zombies.Zombie;

import java.util.List;

public class Row {
    private List<Cell> cells;
    private int index;
    private boolean hasZombie;
    private List<Zombie> activeZombies;
    private List<Projectile> activeProjectiles;
    private Lawnmower lawnmower;
    public Cell cellAt(int y){return null;}
    public void setLawnmower(){};
    public Plant frontPlant(){return null;}



}
