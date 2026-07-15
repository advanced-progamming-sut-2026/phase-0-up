package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.game.GameSession;

import java.util.List;

// LANE_CLEAR plant-food: deals heavy damage to every zombie in the plant's lane (Citron's plasma ball).
public class LaneClearStrategy implements PlantFoodStrategy {
    private int damage;

    public LaneClearStrategy(int damage) {
        this.damage = damage;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        List<Zombie> zombies = gameSession.getMap().getRow(sourcePlant.getY()).getZombies();
        if (zombies == null) return;

        for (Zombie z : zombies) {
            if (!z.getHealth().isDead()) {
                z.getHealth().applyDamage(damage, Element.NEUTRAL, sourcePlant);
            }
        }
    }
}
