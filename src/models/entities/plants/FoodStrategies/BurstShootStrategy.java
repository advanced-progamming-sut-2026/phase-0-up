package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.Burstable;
import models.entities.plants.abilities.PlantAbility;
import models.game.GameSession;

// PROJECTILE_BURST plant-food: makes the plant's shooter fire a rapid extra volley.
public class BurstShootStrategy implements PlantFoodStrategy {
    private int burstShots;

    public BurstShootStrategy(int burstShots) {
        this.burstShots = burstShots;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof Burstable) {
                ((Burstable) ability).queueBurst(burstShots);
            }
        }
    }
}
