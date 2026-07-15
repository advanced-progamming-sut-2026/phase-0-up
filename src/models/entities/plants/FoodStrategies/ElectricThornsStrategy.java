package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.abilities.ShootProjectileAbility;
import models.game.GameSession;

// ELECTRIC_THORNS plant-food: permanently turns Cactus shots into high-damage, unlimited-pierce electric thorns.
public class ElectricThornsStrategy implements PlantFoodStrategy {
    private static final int UNLIMITED_PIERCE = 99;
    private int damageMultiplier;

    public ElectricThornsStrategy(int damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof ShootProjectileAbility) {
                ((ShootProjectileAbility) ability).upgradeToElectric(damageMultiplier, UNLIMITED_PIERCE);
            }
        }
    }
}
