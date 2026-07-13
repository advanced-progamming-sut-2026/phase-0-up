package models.map.Terrains;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;

public class FrozenTerrain extends Terrain{
    private int hp = 600;
    private Zombie innerZombie;
    private Plant innerPlant;
    private String type;
    private boolean isMelted;

    public FrozenTerrain() {
        this.plantable = true;
        this.isMelted = false;
        this.symbol = '&';
        this.blocksProjectiles = true;
    }

    @Override
    public void takeDamage(int damage, Element element) {
        if (isMelted) return;

        if (element == Element.FIRE) {
            this.damage(this.hp);
            return;
        } else if (element == Element.ICE) {
            return;
        }

        this.damage(damage);
    }

    public void setInner(String type , Zombie z , Plant p) {
        if(type.equalsIgnoreCase("zombie")){
            innerZombie = z;
            z.getState().setFrozen(true);
        } else {
            innerPlant = p;
            p.setFrozen(true);
        }
        this.type = type.toLowerCase();
    }

    public void damage(int rate){
        hp -= rate;
        if(hp <= 0){
            isMelted = true;
            this.effect(innerZombie , innerPlant);
        }
    }

    public void meltByTick(){
        //each second 60 hp and each 10 ticks equal 1 second => each tick 6 hp
        this.damage(6);
    }

    @Override
    public void effect(Zombie z, Plant p) {
        if(type.equals("zombie")){
            z.getState().setFrozen(false);
        } else {
            p.setFrozen(false);
        }
    }

    @Override
    public boolean isDestroyed() {
        return isMelted;
    }
}
