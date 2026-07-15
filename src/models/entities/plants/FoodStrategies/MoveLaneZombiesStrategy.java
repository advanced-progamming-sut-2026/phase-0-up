package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;
import java.util.Random;

// MOVE_LANE_ZOMBIES plant-food: forces every zombie in the plant's lane into a random adjacent lane (Garlic).
public class MoveLaneZombiesStrategy implements PlantFoodStrategy {
    private final Random random = new Random();

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        List<Zombie> zombies = gameSession.getMap().getRow(sourcePlant.getY()).getZombies();
        if (zombies == null) return;

        for (Zombie z : zombies) {
            if (!z.getHealth().isDead()) {
                z.getMovement().startLaneSwitch(randomAdjacentLane(sourcePlant.getY()));
            }
        }
    }

    private int randomAdjacentLane(int y) {
        boolean canUp = y > 0;
        boolean canDown = y < Constants.BOARD_ROWS - 1;
        if (canUp && canDown) {
            return random.nextBoolean() ? y - 1 : y + 1;
        }
        return canUp ? y - 1 : y + 1;
    }
}
