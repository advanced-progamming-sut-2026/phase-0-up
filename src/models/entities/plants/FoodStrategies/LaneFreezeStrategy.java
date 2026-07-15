package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;

import java.util.List;

// LANE_FREEZE plant-food: freezes every zombie in the plant's own lane (Snow Pea).
public class LaneFreezeStrategy implements PlantFoodStrategy {
    private int freezeDurationTicks;

    public LaneFreezeStrategy(int freezeDurationTicks) {
        this.freezeDurationTicks = freezeDurationTicks;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        List<Zombie> zombies = gameSession.getMap().getRow(sourcePlant.getY()).getZombies();
        if (zombies == null) return;

        for (Zombie z : zombies) {
            if (!z.getHealth().isDead()) {
                z.getState().applyFreeze(freezeDurationTicks);
            }
        }
    }
}
