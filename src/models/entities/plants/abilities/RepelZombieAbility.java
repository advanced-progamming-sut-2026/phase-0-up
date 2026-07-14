package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.Random;

// Forces the zombie biting the plant into a random adjacent lane (Garlic).
public class RepelZombieAbility extends PlantAbility {
    private final Random random = new Random();

    public RepelZombieAbility() {
        super(0, null); // reacts to being eaten, not the tick loop
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        // no tick behavior
    }

    @Override
    public void onOwnerEaten(Plant owner, Zombie eater, GameSession gameSession) {
        eater.getMovement().startLaneSwitch(randomAdjacentLane(owner.getY()));
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
