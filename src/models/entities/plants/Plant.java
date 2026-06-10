package models.entities.plants;

import models.entities.Entity;
import models.entities.plants.FoodStrategies.CompositePlantFoodStrategy;

import java.util.List;

public class Plant extends Entity {
    protected int level;
    protected int cost;
    protected int recharge;
    protected int actionInterval;
    protected int cooldown;
    protected boolean thisPlantHasFood;
    protected List<PlantTags> tags;
    protected CompositePlantFoodStrategy plantFoodStrategy;

    public void upgrade(){}
    public boolean isReadyToPlant(){return false;}
    @Override
    public void update() {}
    public boolean isDead(){return false;}
}
