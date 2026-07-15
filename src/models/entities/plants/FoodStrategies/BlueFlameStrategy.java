package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.PassiveModifierAbility;
import models.entities.plants.abilities.PlantAbility;
import models.game.GameSession;

// BLUE_FLAME plant-food: permanently upgrades Torchwood's projectile boost (x2 -> x3).
public class BlueFlameStrategy implements PlantFoodStrategy {
    private int damageMultiplier;

    public BlueFlameStrategy(int damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof PassiveModifierAbility) {
                ((PassiveModifierAbility) ability).setDamageMultiplier(damageMultiplier);
            }
        }
    }
}
