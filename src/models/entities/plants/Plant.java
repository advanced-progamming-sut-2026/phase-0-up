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
    protected boolean isFrozen;

    public void upgrade(){}
    public boolean isReadyToPlant(){return false;}
    public boolean isDead(){return false;}
    public void setFrozen(boolean frozen) {isFrozen = frozen;}
    public boolean isFrozen(){return isFrozen;}

    public List<PlantTags> getTags() {
        return tags;
    }
}
