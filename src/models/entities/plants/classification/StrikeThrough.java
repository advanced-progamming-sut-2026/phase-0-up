package models.entities.plants.classification;

import models.entities.plants.Plant;
import models.entities.plants.components.PlantHealthComponent;
import models.entities.projectiles.ProjectileType;

public class StrikeThrough extends Plant {
    private int damage;
    private ProjectileType projectileType;

    public StrikeThrough(String name, int id, double x, int y, PlantHealthComponent health, int level, int cost, boolean isAquatic, int damage, ProjectileType projectileType) {
        super(name, id, x, y, health, level, cost, isAquatic);
        this.damage = damage;
        this.projectileType = projectileType;
    }

    public ProjectileType shoot(){return null;}
}
