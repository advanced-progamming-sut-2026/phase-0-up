package models.map;
import models.entities.plants.Plant;
import models.map.Terrains.Terrain;

public class Cell {
    private int x;
    private int y;
    Plant plant = null;
    Terrain terrain;
    public boolean isPlantable;
    public void addPlant(Plant plant){};
    public void removePlant(){};

}
