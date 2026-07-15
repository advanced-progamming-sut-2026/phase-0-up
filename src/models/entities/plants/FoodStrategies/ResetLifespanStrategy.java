package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.game.GameSession;
import models.map.Cell;
import models.map.Row;

// RESET_LIFESPAN plant-food: resets the lifespan of every planted plant of the same kind (Sea-shroom, Puff-shroom).
public class ResetLifespanStrategy implements PlantFoodStrategy {
    private int lifespanSeconds;

    public ResetLifespanStrategy(int lifespanSeconds) {
        this.lifespanSeconds = lifespanSeconds;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        String kind = sourcePlant.getName();
        for (Row row : gameSession.getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                Plant plant = cell.getCurrentPlant();
                if (plant != null && !plant.isDead() && kind.equals(plant.getName())
                        && plant.getHealth() != null) {
                    plant.getHealth().setLimitedLifespan(lifespanSeconds);
                }
            }
        }
    }
}
