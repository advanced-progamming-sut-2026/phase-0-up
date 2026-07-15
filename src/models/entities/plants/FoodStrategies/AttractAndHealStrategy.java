package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;

// ATTRACT_AND_HEAL plant-food: fully heals the plant and pulls every zombie into its lane (Sweet Potato).
public class AttractAndHealStrategy implements PlantFoodStrategy {
    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        if (sourcePlant.getHealth() != null) {
            sourcePlant.getHealth().heal(sourcePlant.getHealth().getMaxHp());
        }

        int ownLane = sourcePlant.getY();
        for (int row = 0; row < Constants.BOARD_ROWS; row++) {
            if (row == ownLane) continue;

            List<Zombie> zombies = gameSession.getMap().getRow(row).getZombies();
            if (zombies == null) continue;
            for (Zombie z : zombies) {
                if (!z.getHealth().isDead()) {
                    z.getMovement().startLaneSwitch(ownLane);
                }
            }
        }
    }
}
