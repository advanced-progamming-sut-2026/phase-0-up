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
        // Not falling: this sun comes out of the plant, not out of the sky. See ProduceSunAbility --
        // marking a plant-made sun as falling makes it drop in from above the board and skips the
        // producer's animation.
        Sun sun = new Sun(sourcePlant.getX(), targetY, targetY, SunType.NORMAL, sunAmount, false, 100);
        gameSession.addSun(sun);
    }
}
