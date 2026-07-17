package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;

// MAP_WIDE_FREEZE plant-food: freezes every zombie on the board (Kernel-pult, Iceberg Lettuce).
public class MapWideFreezeStrategy implements PlantFoodStrategy {
    private int freezeDurationTicks;

    public MapWideFreezeStrategy(int freezeDurationTicks) {
        this.freezeDurationTicks = freezeDurationTicks;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (int row = 0; row < Constants.BOARD_ROWS; row++) {
            List<Zombie> zombies = gameSession.getMap().getRow(row).getZombies();
            if (zombies == null) continue;

            for (Zombie z : zombies) {
                if (z.isTargetable()) {
                    z.getState().applyFreeze(freezeDurationTicks);
                }
            }
        }
    }
}
