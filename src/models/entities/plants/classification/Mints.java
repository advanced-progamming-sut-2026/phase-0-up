package models.entities.plants.classification;

import models.entities.plants.Plant;
import models.entities.plants.components.PlantHealthComponent;

import java.util.List;

public class Mints extends Plant {
    public Mints(String name, int id, double x, int y, PlantHealthComponent health, int level, int cost, boolean isAquatic) {
        super(name, id, x, y, health, level, cost, isAquatic);
    }

    public void givePlantFood(List<Plant> allPlants){}
}
