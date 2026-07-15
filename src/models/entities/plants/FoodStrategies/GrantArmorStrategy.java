package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.game.GameSession;

// GRANT_PERMANENT_ARMOR plant-food: permanently raises the plant's max HP and heals it (wall-nuts).
public class GrantArmorStrategy implements PlantFoodStrategy {
    private int armorHp;

    public GrantArmorStrategy(int armorHp) {
        this.armorHp = armorHp;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        if (sourcePlant.getHealth() != null) {
            sourcePlant.getHealth().setMaxHp(sourcePlant.getHealth().getMaxHp() + armorHp);
            sourcePlant.getHealth().heal(armorHp);
        }
    }
}
