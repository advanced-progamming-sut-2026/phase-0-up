package models.map;
import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.Trajectory;
import models.map.Terrains.Terrain;
import utils.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Cell {
    private double x;
    private int y;
    private Plant currentPlant;
    private Plant protector;
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

    public Plant getCurrentPlant(){
        return currentPlant;
    }

    public Plant getProtector(){
        return protector;
    }

    public boolean hasProtector(){
        return protector != null;
    }

    // Zombies bite the protective cover (Pumpkin) first, then the base plant.
    public Plant getDefendingPlant(){
        return (protector != null) ? protector : currentPlant;
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

        if (!isPlantable) return new Result(false, "This cell is not plantable!");

        if (newPlant.isAquatic() && !isFlooded) return new Result(false, "This plant must be planted in water!");
        if (!newPlant.isAquatic() && isFlooded) return new Result(false, "You can't plant this on water!");

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

    private Result addProtector(Plant newPlant){
        if (protector != null) return new Result(false, "This cell is already protected!");
        if (!isPlantable) return new Result(false, "This cell is not plantable!");
        if (newPlant.isAquatic() && !isFlooded) return new Result(false, "This plant must be planted in water!");
        if (!newPlant.isAquatic() && isFlooded) return new Result(false, "You can't plant this on water!");

        this.protector = newPlant;
        return new Result(true, "Protective cover placed.");
    }

    public void interactWithProjectile(Projectile projectile){
        if (this.currentPlant != null && this.currentPlant.getName().equals("Torchwood")) {
            if (projectile.getTrajectory() == Trajectory.DIRECT && projectile.getElement() == Element.NEUTRAL) {
                projectile.setElement(Element.FIRE);
                projectile.setDamage(projectile.getDamage() * 2);
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
