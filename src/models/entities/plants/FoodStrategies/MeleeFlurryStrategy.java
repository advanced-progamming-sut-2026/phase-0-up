package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.MeleeAttackAbility;
import models.entities.plants.abilities.PlantAbility;
import models.game.GameSession;

// MELEE_FLURRY plant-food: a rapid flurry of area strikes over a duration (Bonk Choy, Wasabi Whip).
public class MeleeFlurryStrategy implements PlantFoodStrategy {
    private int durationTicks;

    public MeleeFlurryStrategy(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof MeleeAttackAbility) {
                ((MeleeAttackAbility) ability).activatePlantFoodFlurry(durationTicks);
            }
        }
    }
}
