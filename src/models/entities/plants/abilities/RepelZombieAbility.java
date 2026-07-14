package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;
import java.util.Random;

// Forces zombies biting the plant into a random adjacent lane (Garlic). Pair with a contact trigger.
public class RepelZombieAbility extends PlantAbility {
    private final Random random = new Random();

    public RepelZombieAbility(int actionInterval, TriggerStrategy triggerStrategy) {
        super(actionInterval, triggerStrategy);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        List<Zombie> zombies = gameSession.getMap().getRow(owner.getY()).getZombies();
        if (zombies == null) return;

        for (Zombie z : zombies) {
            if (!z.getHealth().isDead()
                    && Math.abs(z.getMovement().getPositionX() - owner.getX()) <= 0.5) {
                z.getMovement().startLaneSwitch(randomAdjacentLane(owner.getY()));
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
