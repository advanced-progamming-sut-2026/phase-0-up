package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;

public abstract class GraveTerrain extends Terrain{
    public static final int DEFAULT_GRAVE_HP = 700;

    protected int hp;
    protected boolean isDead = false;

    public GraveTerrain() {
        this.hp = DEFAULT_GRAVE_HP;
        this.plantable = false;
        this.symbol = '#';
        this.blocksProjectiles = true;
    }

    // Remaining health, so "show tile status" can report how much of the headstone is left. Subclasses
    // set hp in their own constructors, so this reads the field rather than a constructor argument.
    public int getHp() {
        return Math.max(0, hp);
    }

    public int getMaxHp() {
        return DEFAULT_GRAVE_HP;
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

    public abstract void getShot(int damage);
}
