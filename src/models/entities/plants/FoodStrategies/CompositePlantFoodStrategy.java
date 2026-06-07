package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;

import java.util.ArrayList;
import java.util.List;

public class CompositePlantFoodStrategy implements PlantFoodStrategy {
    private List<PlantFoodStrategy> strategies = new ArrayList<>();

    public void addStrategy(PlantFoodStrategy strategy) {};

    @Override
    public void executeEffect(Plant sourcePlant) {};
}
