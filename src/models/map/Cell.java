package models.map;
import models.entities.plants.Plant;
import models.map.Terrains.Terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Cell {
    private int x;
    private int y;
    private Stack<Plant> plantStack;
    private List<Terrain> terrain;
    private boolean isPlantable;
    private boolean isFlooded;

    public Cell(int x, int y, boolean isPlantable) {
        this.x = x;
        this.y = y;
        this.isPlantable = isPlantable;
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

    public Stack<Plant> getPlantStack() {
        return plantStack;
    }
    public void addPlant(Plant plant){plantStack.add(plant);}
    public void removePlant(){plantStack.pop();}

    public int getY() {
        return y;
    }

    public int getX() {
        return x;
    }
}
