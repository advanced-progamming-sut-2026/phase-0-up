package models.entities.plants.classification;

import models.entities.plants.Plant;
import models.entities.collectibles.Sun;
import models.entities.plants.components.PlantHealthComponent;

public class SunProducers extends Plant {
    private int numberOfSuns;
    private boolean pendingSun;

    public SunProducers(String name, int id, double x, int y, PlantHealthComponent health, int level, int cost, boolean isAquatic, int numberOfSuns, boolean pendingSun) {
        super(name, id, x, y, health, level, cost, isAquatic);
        this.numberOfSuns = numberOfSuns;
        this.pendingSun = pendingSun;
    }

    public Sun produceSun(){return null;}
}
