package models.entities.plants.classification;

import models.entities.plants.components.PlantHealthComponent;
import models.entities.projectiles.ProjectileType;
import models.entities.plants.Plant;
import models.map.Cell;

public class Lobbers extends Plant {
    private int damage;

    public Lobbers(String name, int id, double x, int y, PlantHealthComponent health, int level, int cost, boolean isAquatic, int damage) {
        super(name, id, x, y, health, level, cost, isAquatic);
        this.damage = damage;
    }

    public ProjectileType lob(Cell target) {return null;}
}
