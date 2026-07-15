package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.Growable;
import models.entities.plants.abilities.PlantAbility;
import models.game.GameSession;

// Instantly maxes the growth stage of a wramp-up plant's abilities (Sun-shroom, Kiwibeast).
public class InstantGrowing implements PlantFoodStrategy {
    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof Growable) {
                ((Growable) ability).growToMaxStage();
            }
        }
    }
}
