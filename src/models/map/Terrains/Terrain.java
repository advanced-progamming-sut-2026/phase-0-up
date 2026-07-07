package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.projectiles.DamageType;
import models.entities.zombies.Zombie;

public abstract class Terrain {
    protected boolean plantable;
    protected char symbol;

    protected boolean blocksProjectiles = false;

    public boolean isPlantable() { return this.plantable; }
    public char getSymbol() { return symbol; }
    public boolean doesBlockProjectiles() { return blocksProjectiles; }

    public abstract void effect(Zombie z , Plant p);


    public void takeDamage(int damage, DamageType damageType) {
        // default: terrains doesn't take damage
    }

    //a method for removing terrain from cell if destroyed
    public boolean isDestroyed() {
        return false;
    }
}
