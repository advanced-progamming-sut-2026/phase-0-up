package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;

public class GraveTerrain extends Terrain{
    protected int hp;
    protected boolean isDead = false;

    public GraveTerrain() {
        this.hp = 700;
        this.plantable = false;
        this.symbol = '#';
        this.blocksProjectiles = true;
    }

    @Override
    public void takeDamage(int damage, Element element) {
        if (element == Element.POISON) return;
        //poison shots doesn't damage graves
        this.hp -= damage;
        if(this.hp <= 0) {
            this.hp = 0;
            this.isDead = true;
        }
    }

    @Override
    public boolean isDestroyed() {
        return isDead;
    }

    @Override
    public void effect(Zombie z, Plant p) {

    }
}
