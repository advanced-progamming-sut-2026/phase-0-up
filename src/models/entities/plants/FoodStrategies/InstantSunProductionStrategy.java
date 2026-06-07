package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;

public class InstantSunProductionStrategy implements PlantFoodStrategy{
    private int sunAmount;

    public InstantSunProductionStrategy(int sunAmount) {};
    @Override
    public void executeEffect(Plant sourcePlant) {};
}
