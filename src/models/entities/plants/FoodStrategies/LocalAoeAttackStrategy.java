package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.MeleeAttackAbility;
import models.entities.plants.abilities.PlantAbility;
import models.game.GameSession;

// LOCAL_AOE_ATTACK plant-food: a powerful boosted version of the plant's own melee strike
// (Bonk Choy, Phat Beet, Wasabi Whip, Kiwibeast).
public class LocalAoeAttackStrategy implements PlantFoodStrategy {
    private int damageMultiplier;

    public LocalAoeAttackStrategy(int damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof MeleeAttackAbility) {
                ((MeleeAttackAbility) ability).plantFoodStrike(sourcePlant, gameSession, damageMultiplier);
            }
        }
    }
}
