package models.entities.plants.FoodStrategies;

import models.entities.collectibles.Sun;
import models.entities.collectibles.SunType;
import models.entities.plants.Plant;
import models.game.GameSession;

// SPAWN_SUN_ITEMS plant-food: drops a collectible sun worth the given amount at the plant.
public class InstantSunProductionStrategy implements PlantFoodStrategy {
    private int sunAmount;

    public InstantSunProductionStrategy(int sunAmount) {
        this.sunAmount = sunAmount;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        double targetY = sourcePlant.getY() + 0.3;
        Sun sun = new Sun(sourcePlant.getX(), sourcePlant.getY(), targetY, SunType.NORMAL, sunAmount, true, 100);
        gameSession.addSun(sun);
    }
}
