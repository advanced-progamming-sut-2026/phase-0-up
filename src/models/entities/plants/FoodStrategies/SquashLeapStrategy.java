package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.abilities.SquashAbility;
import models.game.GameSession;

// SQUASH_LEAP plant food: the Squash jumps again, and again.
//
// It used to be DESTROY_RANDOM, which deleted N zombies from anywhere on the board -- no jump, no
// travel, nothing on screen, and a squash in row 0 quietly killing something in row 4. The plant's
// own art ships two clips for exactly this (plantfood_jump_down_left/right) and nothing played them.
//
// Each queued leap picks its own target when it is taken, so a fed squash works through whatever has
// come into reach rather than committing to a list up front.
public class SquashLeapStrategy implements PlantFoodStrategy {
    private final int extraLeaps;

    public SquashLeapStrategy(int extraLeaps) {
        this.extraLeaps = extraLeaps;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof SquashAbility squash) {
                squash.queueExtraLeaps(extraLeaps);
                return;
            }
        }
    }
}
