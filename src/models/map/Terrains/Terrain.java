package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;

public abstract class Terrain {
    protected boolean plantable;
    protected char symbol;
    public boolean isPlantable(){return this.plantable;}
    public abstract void effect(Zombie z , Plant p);
    public char getSymbol(){return symbol;}

}
