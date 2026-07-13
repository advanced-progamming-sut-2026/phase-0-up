package models.entities.plants.classification;

import models.entities.plants.components.PlantHealthComponent;
import models.entities.projectiles.ProjectileType;
import models.entities.plants.Plant;

public class Shooters extends Plant {
    private int damage;
    private int shotsPerAction;
    private ProjectileType projectileType;

    public Shooters(String name, int id, double x, int y, PlantHealthComponent health, int level, int cost, boolean isAquatic, int damage, int shotsPerAction, ProjectileType projectileType) {
        super(name, id, x, y, health, level, cost, isAquatic);
        this.damage = damage;
        this.shotsPerAction = shotsPerAction;
        this.projectileType = projectileType;
    }

    public ProjectileType shoot(){return null;}
}
