package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.game.GameSession;

// Pure defensive wall: blocks zombies by its HP, no active per-tick behavior (Wall-nut, Tall-nut, Pumpkin).
public class PassiveShieldAbility extends PlantAbility {
    public PassiveShieldAbility() {
        super(0, null); // null trigger => never executes
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        // intentionally empty
    }
}
