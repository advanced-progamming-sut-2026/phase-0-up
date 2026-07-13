package models.entities.plants.classification;

import models.entities.plants.Plant;
import models.entities.plants.components.PlantHealthComponent;
import models.entities.zombies.Zombie;

public class Homing extends Plant {
    private int damage;

    public Homing(String name, int id, double x, int y, PlantHealthComponent health, int level, int cost, boolean isAquatic, int damage) {
        super(name, id, x, y, health, level, cost, isAquatic);
        this.damage = damage;
    }

    public Zombie findTheTarget(){return null;}
    public void doingTheTask(Zombie target){}
}
