package models.map;
import models.entities.plants.Plant;
import models.entities.plants.abilities.PassiveModifierAbility;
import models.entities.plants.abilities.PlantAbility;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
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

        this.currentPlant = newPlant;
        return new Result(true, "Plant placed successfully.");
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

        this.platform = newPlant;
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

        this.protector = newPlant;
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
