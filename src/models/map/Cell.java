package models.map;
import models.entities.plants.Plant;
import models.map.Terrains.Terrain;

import java.util.Stack;

public class Cell {
    private int x;
    private int y;
    private Stack<Plant> plantStack;
    private Terrain terrain;
    private boolean isPlantable;

    public Cell(int x, int y, boolean isPlantable) {
        this.x = x;
        this.y = y;
        this.isPlantable = isPlantable;
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

    public void addPlant(Plant plant){plantStack.add(plant);}
    public void removePlant(){plantStack.pop();}
}
