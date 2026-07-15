package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.game.GameSession;

public interface PlantFoodStrategy {
    void executeEffect(Plant sourcePlant, GameSession gameSession);
}
