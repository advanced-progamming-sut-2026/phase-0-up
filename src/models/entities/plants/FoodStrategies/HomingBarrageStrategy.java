package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.GlobalTargetingAbility;
import models.entities.plants.abilities.PlantAbility;
import models.game.GameSession;

// HOMING_BARRAGE plant-food: fires a rapid burst of homing shots at zombies (Cat-tail).
public class HomingBarrageStrategy implements PlantFoodStrategy {
    private int count;

    public HomingBarrageStrategy(int count) {
        this.count = count;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof GlobalTargetingAbility) {
                ((GlobalTargetingAbility) ability).queueBurst(count);
            }
        }
    }
}
