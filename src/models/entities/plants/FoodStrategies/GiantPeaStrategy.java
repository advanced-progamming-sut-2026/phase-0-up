package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.abilities.ShootProjectileAbility;
import models.game.GameSession;

// GIANT_PEA_BURST plant-food: fires N giant peas (20x damage) from the plant's shooter (Repeater, Pea Pod, ...).
public class GiantPeaStrategy implements PlantFoodStrategy {
    private static final int GIANT_DAMAGE_MULTIPLIER = 20;
    private int count;

    public GiantPeaStrategy(int count) {
        this.count = count;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof ShootProjectileAbility) {
                ((ShootProjectileAbility) ability).queueGiantShots(count, GIANT_DAMAGE_MULTIPLIER);
            }
        }
    }
}
