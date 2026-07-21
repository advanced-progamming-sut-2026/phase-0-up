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
        this.plantable = false;
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

    // 60 HP per in-game second, spread over the ticks in a second. Derived from the constants rather
    // than hard-coded, so the melt rate cannot silently drift if either value is retuned.
    public void meltByTick(){
        this.damage(utils.Constants.MELT_RATE_PER_SECOND / utils.Constants.TICKS_PER_SECOND);
    }

    // Frees whatever the block held once it melts. A plain obstacle block (an authored '&' with no
    // inner plant or zombie) has a null type -- there is nothing to free, so it just melts away.
    @Override
    public void effect(Zombie z, Plant p) {
        if (type == null) {
            return;
        }
        if (type.equals("zombie")) {
            if (z != null) {
                z.getState().setFrozen(false);
            }
        } else if (p != null) {
            p.setFrozen(false);
        }
    }

    @Override
    public boolean isDestroyed() {
        return isMelted;
    }
}
