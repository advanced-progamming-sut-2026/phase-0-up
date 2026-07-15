package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;

// MAP_WIDE_BUTTER plant-food: butters (stuns) every zombie on the board (Kernel-pult).
public class MapWideButterStrategy implements PlantFoodStrategy {
    private int butterDurationTicks;

    public MapWideButterStrategy(int butterDurationTicks) {
        this.butterDurationTicks = butterDurationTicks;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (int row = 0; row < Constants.BOARD_ROWS; row++) {
            List<Zombie> zombies = gameSession.getMap().getRow(row).getZombies();
            if (zombies == null) continue;

            for (Zombie z : zombies) {
                if (!z.getHealth().isDead()) {
                    z.getState().applyButter(butterDurationTicks);
                }
            }
        }
    }
}
