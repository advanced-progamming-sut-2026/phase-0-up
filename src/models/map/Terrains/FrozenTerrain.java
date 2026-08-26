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

    // What this block is holding, for a view that has to decide who draws it.
    //
    // The block is drawn by whatever is INSIDE it -- PlantRenderer and ZombieRenderer both wrap a frozen
    // entity in one, because the model can freeze an entity without creating any terrain at all (three
    // chills, an Iceberg Lettuce) and those cases have no FrozenTerrain to draw from. TerrainRenderer
    // falls back to drawing the block itself when there is nobody left to do it: an empty authored
    // obstacle, or one whose occupant has died inside it and would otherwise leave an invisible wall.
    public Plant getInnerPlant() {
        return innerPlant;
    }

    public Zombie getInnerZombie() {
        return innerZombie;
    }

    // What this block is holding: "zombie", "plant", or null for a plain authored obstacle. The
    // terminal only ever needed the symbol, but the game ships a DIFFERENT ice block for a caged plant
    // and a caged zombie -- they are different shapes -- so a view that draws the block has to ask.
    public String getInnerType() {
        return type;
    }

    // What is left of the block, for a view that flashes it when it is hit. A block takes thousands of
    // points across a level and has no damage clips of its own, so without this a shot into one
    // registers as nothing at all -- the same gap the graves had before T8.4.
    public int getHp() {
        return hp;
    }
}
