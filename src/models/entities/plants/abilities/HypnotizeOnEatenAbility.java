package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;

// Hypnotizes the zombie that bites the plant, then the plant is consumed (Hypno-shroom).
public class HypnotizeOnEatenAbility extends PlantAbility {
    public HypnotizeOnEatenAbility() {
        super(0, null); // reacts to being eaten, not the tick loop
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        // no tick behavior
    }

    @Override
    public void onOwnerEaten(Plant owner, Zombie eater, GameSession gameSession) {
        eater.getState().setHypnotized(true);
        if (owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }
}
