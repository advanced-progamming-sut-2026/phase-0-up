package models.entities.plants.classification;

import models.entities.plants.Plant;
import models.entities.plants.components.PlantHealthComponent;

public class Explosives extends Plant {
    private int damage;
    private int blastSize;

    public Explosives(String name, int id, double x, int y, PlantHealthComponent health, int level, int cost, boolean isAquatic, int damage, int blastSize) {
        super(name, id, x, y, health, level, cost, isAquatic);
        this.damage = damage;
        this.blastSize = blastSize;
    }

    public void explosion(){}
}
