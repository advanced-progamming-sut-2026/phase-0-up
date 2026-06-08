package models.entities.plants;

import models.entities.Entity;
import models.entities.plants.FoodStrategies.CompositePlantFoodStrategy;

import java.util.List;

public class Plant extends Entity {
    protected int level;
    protected int cost;
    protected double recharge;
    protected double actionInterval;
    protected double cooldown;
    protected boolean thisPlantHasFood;
    protected List<PlantTags> tags;
    protected CompositePlantFoodStrategy plantFoodStrategy;

    public void upgrade(){}
    public boolean isReadyToPlant(){return false;}
    @Override
    public void update() {}
    public boolean isDead(){return false;}
}
