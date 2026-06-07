package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.projectiles.ProjectileType;
import models.entities.projectiles.ShootPattern;

public class BurstShootStrategy implements PlantFoodStrategy{
    private ProjectileType projectileType;
    private int projectileCount;
    private ShootPattern pattern;

    public BurstShootStrategy(ProjectileType type, int count, ShootPattern pattern) {}
    @Override
    public void executeEffect(Plant sourcePlant) {};
}
