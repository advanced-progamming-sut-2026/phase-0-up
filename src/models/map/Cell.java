package models.map;
import models.entities.plants.Plant;
import models.map.Terrains.Terrain;

import java.util.Stack;

public class Cell {
    private int x;
    private int y;
    private Stack<Plant> plantStack;
    private Terrain terrain;
    public boolean isPlantable;
    public void addPlant(Plant plant){};
    public void removePlant(){};

}
