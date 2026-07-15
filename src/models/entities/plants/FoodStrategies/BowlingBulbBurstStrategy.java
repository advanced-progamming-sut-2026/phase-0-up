package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.BowlingBulbAbility;
import models.entities.plants.abilities.PlantAbility;
import models.game.GameSession;

// EXPLOSIVE_BULB_BURST plant-food: fires N large 600-damage plasma balls, staggered (Bowling Bulb).
public class BowlingBulbBurstStrategy implements PlantFoodStrategy {
    private int count;

    public BowlingBulbBurstStrategy(int count) {
        this.count = count;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof BowlingBulbAbility) {
                ((BowlingBulbAbility) ability).queuePlantFoodBalls(count);
            }
        }
    }
}
