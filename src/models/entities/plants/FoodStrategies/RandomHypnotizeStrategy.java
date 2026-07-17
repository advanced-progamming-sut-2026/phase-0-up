package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// RANDOM_HYPNOTIZE plant-food: hypnotizes up to N random zombies (Caulipower, Electric Blueberry, Hypno-shroom).
public class RandomHypnotizeStrategy implements PlantFoodStrategy {
    private int count;

    public RandomHypnotizeStrategy(int count) {
        this.count = count;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        List<Zombie> alive = new ArrayList<>();
        for (int row = 0; row < Constants.BOARD_ROWS; row++) {
            List<Zombie> zombies = gameSession.getMap().getRow(row).getZombies();
            if (zombies == null) continue;
            for (Zombie z : zombies) {
                if (z.isTargetable()) {
                    alive.add(z);
                }
            }
        }

        Collections.shuffle(alive);
        for (int i = 0; i < count && i < alive.size(); i++) {
            alive.get(i).getState().setHypnotized(true);
        }
    }
}
