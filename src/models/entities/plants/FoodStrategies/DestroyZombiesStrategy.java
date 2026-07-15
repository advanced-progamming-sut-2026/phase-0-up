package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Instantly destroys up to N zombies: front-to-back for Tangle Kelp/Chomper ("pull underwater"),
// or random for Electric Blueberry.
public class DestroyZombiesStrategy implements PlantFoodStrategy {
    private int count;
    private boolean random;

    public DestroyZombiesStrategy(int count, boolean random) {
        this.count = count;
        this.random = random;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        List<Zombie> targets = new ArrayList<>();
        for (int row = 0; row < Constants.BOARD_ROWS; row++) {
            List<Zombie> zombies = gameSession.getMap().getRow(row).getZombies();
            if (zombies == null) continue;
            for (Zombie z : zombies) {
                if (!z.getHealth().isDead()) {
                    targets.add(z);
                }
            }
        }

        if (random) {
            Collections.shuffle(targets);
        }

        for (int i = 0; i < count && i < targets.size(); i++) {
            targets.get(i).getHealth().applyDamage(Integer.MAX_VALUE, Element.NEUTRAL, sourcePlant);
        }
    }
}
