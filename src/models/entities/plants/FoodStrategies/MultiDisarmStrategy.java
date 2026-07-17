package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;

// MULTI_DISARM plant-food: strips metallic armor from up to N zombies at once (Magnet-shroom).
public class MultiDisarmStrategy implements PlantFoodStrategy {
    private int count;

    public MultiDisarmStrategy(int count) {
        this.count = count;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        int disarmed = 0;
        for (int row = 0; row < Constants.BOARD_ROWS && disarmed < count; row++) {
            List<Zombie> zombies = gameSession.getMap().getRow(row).getZombies();
            if (zombies == null) continue;

            for (Zombie z : zombies) {
                if (disarmed >= count) break;
                if (z.isTargetable() && z.getHealth().tryRemoveMetallicArmor()) {
                    disarmed++;
                }
            }
        }
    }
}
