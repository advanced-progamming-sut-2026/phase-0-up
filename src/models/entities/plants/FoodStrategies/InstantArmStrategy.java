package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.DelayedExplosiveAbility;
import models.entities.plants.abilities.PlantAbility;
import models.game.GameSession;

// INSTANT_ARM plant-food: arms a mine immediately, skipping its arm delay (Potato Mine, Primal Potato Mine).
public class InstantArmStrategy implements PlantFoodStrategy {
    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof DelayedExplosiveAbility) {
                ((DelayedExplosiveAbility) ability).armInstantly();
            }
        }
    }
}
