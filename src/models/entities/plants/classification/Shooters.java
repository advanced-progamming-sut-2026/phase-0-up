package models.entities.plants.classification;

import models.entities.projectiles.ProjectileType;
import models.entities.plants.Plant;

public class Shooters extends Plant {
    private int damage;
    private int shotsPerAction;
    private ProjectileType projectileType;

    public ProjectileType shoot(){return null;}
}
