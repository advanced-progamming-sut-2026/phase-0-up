package models.map;
import models.entities.plants.Plant;
import models.entities.plants.abilities.PassiveModifierAbility;
import models.entities.plants.abilities.GraveBusterAbility;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.abilities.WarmthAbility;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
import models.map.Terrains.FrozenTerrain;
import models.map.Terrains.GraveTerrain;
import models.map.Terrains.Terrain;
import utils.Result;

import java.util.ArrayList;
import java.util.List;

public class Cell {
    private double x;
    private int y;
    private Plant currentPlant;
    private Plant protector;
    private Plant platform;
    private List<Terrain> terrain;
    private boolean isPlantable;
    private boolean isFlooded;

    public Cell(double x, int y, boolean isPlantable) {
        this.x = x;
        this.y = y;
        this.isPlantable = isPlantable;
        this.currentPlant = null;
        terrain = new ArrayList<>();
        isFlooded = false;
    }

    public List<Terrain> getTerrain() {
        return terrain;
    }

    public boolean isFlooded() {
        return isFlooded;
    }

    public void setFlooded(boolean flooded) {
        isFlooded = flooded;
    }

    public void addTerrain(Terrain terrain) {
        this.terrain.add(terrain);
    }

    // Drops any terrain that has been destroyed (a grave shot to 0 HP, a melted ice block) off the
    // tile. Damage and removal are separate steps -- a terrain marks itself destroyed but cannot pull
    // itself out of the cell it does not know about -- so the sweep is centralised here and run every
    // tick. Without it a broken grave lingers in the list, and everything that scans terrain has to
    // remember to re-check isDestroyed() forever. Returns true if anything was actually removed.
    public boolean removeDestroyedTerrain() {
        if (terrain == null || terrain.isEmpty()) {
            return false;
        }
        return terrain.removeIf(Terrain::isDestroyed);
    }

    public Plant getCurrentPlant(){
        return currentPlant;
    }

    public Plant getProtector(){
        return protector;
    }

    public boolean hasProtector(){
        return protector != null;
    }

    public Plant getPlatform(){
        return platform;
    }

    public boolean hasPlatform(){
        return platform != null;
    }

    // Zombies bite top-down: cover (Pumpkin), then the base plant, then the platform (Lily Pad).
    public Plant getDefendingPlant(){
        if (protector != null && !protector.isDead()) {
            return protector;
        }
        if (currentPlant != null && !currentPlant.isDead()) {
            return currentPlant;
        }
        return platform;
    }

    public int getY() {
        return y;
    }

    public double getX() {
        return x;
    }

    public boolean hasPlant(){
        return this.currentPlant != null;
    }

    public Result addPlant(Plant newPlant){
        if (newPlant.isProtector()) {
            return addProtector(newPlant);
        }
        if (newPlant.isPlatform()) {
            return addPlatform(newPlant);
        }

        if (this.hasPlant()) {

            if (this.currentPlant.getStackableComponent() != null) {

                if (this.currentPlant.getName().equals(newPlant.getName())) {

                    boolean stacked = this.currentPlant.getStackableComponent().addStack();

                    if (stacked) {
                        int heads = this.currentPlant.getStackableComponent().getCurrentStacks();
                        return new Result(true, "Pea Pod stacked successfully! Current heads: " + heads);
                    } else {
                        return new Result(false, "This Pea Pod is already at maximum capacity (5).");
                    }
                }
            }
            return new Result(false, "This cell is already occupied!");
        }

        // Grave Buster is the exception: a grave is the only tile it may go on, and also unplantable.
        if (isGraveBuster(newPlant)) {
            if (!hasLiveGrave()) {
                return new Result(false, "Grave Buster only goes on a grave. Find a headstone to chew!");
            }
            this.currentPlant = settle(newPlant);
            return new Result(true, "Plant placed successfully.");
        }
        // A plant that radiates heat is the second exception, for the same reason as the first: an ice
        // block is the one tile Hot Potato is FOR, and an ice block is not plantable. A plant whose job
        // is removing an obstacle has to be allowed onto the obstacle.
        //
        // A block holding a frozen PLANT is not covered: the cell already has a plant in it and the
        // occupied check above turns it away, which is right -- you thaw that one from beside it.
        if (isThawer(newPlant) && hasFrozenBlock()) {
            this.currentPlant = settle(newPlant);
            return new Result(true, "Plant placed successfully.");
        }
        // Use the terrain-aware check, not the raw field: a live grave (or any non-plantable terrain)
        // sitting on this tile must block planting.
        if (!isPlantable()) return new Result(false, "This cell is not plantable!");

        if (isFlooded) {
            // on water a plant must be aquatic, or sit on a Lily Pad platform
            if (!newPlant.isAquatic() && platform == null) {
                return new Result(false, "You need a Lily Pad to plant this on water!");
            }
        } else if (newPlant.isAquatic()) {
            return new Result(false, "This plant must be planted in water!");
        }

        this.currentPlant = settle(newPlant);
        return new Result(true, "Plant placed successfully.");
    }

    // A plant that has just been accepted by this cell takes the cell's coordinates.
    //
    // It used to keep whatever it was built with, which was invisible for as long as the only way into
    // a cell was PlantFactory creating the plant AT that cell. The Fisherman Zombie broke that: it
    // reels a LIVE plant one tile along, so the plant sat in its new cell still believing it was in the
    // old one -- and every single thing that asks a plant where it is was then wrong about it.
    //
    // The visible one was the flashing. PlantRenderer.drawCell draws from the CELL's column while
    // redraw() -- the pass that stamps a shooter back over its own projectile -- reads plant.getX(),
    // so a reeled Peashooter was drawn in its new tile and again in its old one, every frame it had a
    // pea in the air. The quieter ones are worse: its shots spawned from the old tile, its blast
    // radius was centred there, and its own narration reported the wrong square.
    //
    // Done here rather than in the Fisherman, so the invariant is "a plant in a cell is at that cell"
    // and anything that moves a plant later inherits it. Only ever on a placement that SUCCEEDED --
    // a refused move must not teleport the plant it just refused.
    private Plant settle(Plant plant) {
        plant.setX(this.x);
        plant.setY(this.y);
        return plant;
    }

    public Result removePlant(){
        if(this.currentPlant == null){
            return new Result(false , "This cell does not contain a plant.");
        }
        this.currentPlant = null;
        return new Result(true, "Plant removed successfully.");
    }

    public Result removeProtector(){
        if(this.protector == null){
            return new Result(false , "This cell has no protective cover.");
        }
        this.protector = null;
        return new Result(true, "Protective cover removed.");
    }

    public Result removePlatform(){
        if(this.platform == null){
            return new Result(false , "This cell has no platform.");
        }
        this.platform = null;
        return new Result(true, "Platform removed.");
    }

    private Result addPlatform(Plant newPlant){
        if (platform != null) return new Result(false, "This cell already has a platform!");
        if (!isPlantable) return new Result(false, "This cell is not plantable!");
        if (!isFlooded) return new Result(false, "A Lily Pad must be placed on water!");

        this.platform = settle(newPlant);
        return new Result(true, "Platform placed.");
    }

    private Result addProtector(Plant newPlant){
        if (protector != null) return new Result(false, "This cell is already protected!");
        if (!isPlantable) return new Result(false, "This cell is not plantable!");

        // a Lily Pad platform already floats the tile, so a land cover may sit on water
        if (platform == null) {
            if (newPlant.isAquatic() && !isFlooded) return new Result(false, "This plant must be planted in water!");
            if (!newPlant.isAquatic() && isFlooded) return new Result(false, "You can't plant this on water!");
        }

        this.protector = settle(newPlant);
        return new Result(true, "Protective cover placed.");
    }

    public void interactWithProjectile(Projectile projectile){
        if (this.currentPlant != null) {
            for (PlantAbility ability : this.currentPlant.getAbilities()) {
                if (ability instanceof PassiveModifierAbility) {
                    ((PassiveModifierAbility) ability).applyTo(projectile);
                }
            }
        }

        if (this.isFlooded && projectile.getElement() == Element.FIRE) {
            projectile.setElement(Element.NEUTRAL);
            projectile.setDamage(projectile.getDamage() / 2);
        }
    }

    // Identified by its ability, not its name, so renaming it in plants.json cannot break placement.
    private boolean isGraveBuster(Plant plant) {
        return plant != null && plant.getAbilities() != null
                && plant.getAbilities().stream().anyMatch(a -> a instanceof GraveBusterAbility);
    }
    private boolean isThawer(Plant plant) {
        return plant != null && plant.getAbilities() != null
                && plant.getAbilities().stream().anyMatch(a -> a instanceof WarmthAbility);
    }

    // An ice block still standing on this tile.
    public boolean hasFrozenBlock() {
        return terrain != null
                && terrain.stream().anyMatch(t -> t instanceof FrozenTerrain && !t.isDestroyed());
    }

    // A headstone still standing on this tile.
    public boolean hasLiveGrave() {
        return terrain != null
                && terrain.stream().anyMatch(t -> t instanceof GraveTerrain && !t.isDestroyed());
    }
    public boolean isPlantable() {
        if (!this.isPlantable) {
            return false;
        }
        if (this.terrain != null) {
            for (Terrain t : this.terrain) {
                if (!t.isPlantable() && !t.isDestroyed()) {
                    return false;
                }
            }
        }
        return true;
    }
}
