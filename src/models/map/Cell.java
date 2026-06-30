package models.map;
import models.entities.plants.Plant;
import models.map.Terrains.Terrain;
import utils.Result;

import java.util.Stack;

public class Cell {
    private int x;
    private int y;
    private Plant currentPlant;
    private Terrain terrain;
    private boolean isPlantable;

    public Cell(int x, int y, boolean isPlantable) {
        this.x = x;
        this.y = y;
        this.isPlantable = isPlantable;
        this.currentPlant = null;
    }

    public Terrain getTerrain() {
        return terrain;
    }

    public void setTerrain(Terrain terrain) {
        this.terrain = terrain;
    }

    public boolean isPlantable() {
        return isPlantable;
    }

    public Result addPlant(Plant newPlant){
        if (this.currentPlant != null) {

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

    public void removePlant(){}
}
