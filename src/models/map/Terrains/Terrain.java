package models.map.Terrains;

import models.entities.zombies.Zombie;

public abstract class Terrain {
    protected boolean plantable;
    public abstract boolean isPlantable();
    public abstract void onZombieEnter(Zombie z);
    public abstract char symbol();

}
