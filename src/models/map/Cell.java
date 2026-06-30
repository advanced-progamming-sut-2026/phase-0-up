package models.map;
import models.entities.plants.Plant;
import models.map.Terrains.Terrain;
import utils.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Cell {
    private int x;
    private int y;
    private Plant currentPlant;
    private List<Terrain> terrain;
    private boolean isPlantable;
    private boolean isFlooded;

    public Cell(int x, int y, boolean isPlantable) {
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

    public boolean isPlantable() {
        return isPlantable;
    }

    public Plant getCurrentPlant(){
        return currentPlant;
    }

    public int getY() {
        return y;
    }

    public int getX() {
        return x;
    }

    public boolean hasPlant(){
        return this.currentPlant != null;
    }

    public Result addPlant(Plant newPlant){
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
}
