package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.abilities.ReflectDamageAbility;
import models.game.GameSession;

// BOOST_REFLECT plant-food: permanently increases the plant's reflected bite damage (Endurian).
public class BoostReflectStrategy implements PlantFoodStrategy {
    private int amount;

    public BoostReflectStrategy(int amount) {
        this.amount = amount;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof ReflectDamageAbility) {
                ((ReflectDamageAbility) ability).boostReflect(amount);
            }
        }
    }
}
