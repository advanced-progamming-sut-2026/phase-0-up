package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.game.GameSession;

import java.util.ArrayList;
import java.util.List;

// A plant's plant-food effect, composed of one or more sub-effects run together.
public class CompositePlantFoodStrategy implements PlantFoodStrategy {
    private List<PlantFoodStrategy> strategies = new ArrayList<>();

    public void addStrategy(PlantFoodStrategy strategy) {
        if (strategy != null) {
            strategies.add(strategy);
        }
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantFoodStrategy strategy : strategies) {
            strategy.executeEffect(sourcePlant, gameSession);
        }
    }
}
