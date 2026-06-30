package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;

public class GraveTerrain extends Terrain{
    protected int hp;
    protected boolean isDead = false;

    public GraveTerrain() {
        this.hp = 700;
        this.plantable = false;
        this.symbol = '#';
    }

    public void getShot(int damage){
        hp -= damage;
        if(hp<= 0){
            hp = 0;
            isDead = true;
        }
    }

    public boolean isDead() {
        return isDead;
    }

    @Override
    public void effect(Zombie z, Plant p) {

    }
}
