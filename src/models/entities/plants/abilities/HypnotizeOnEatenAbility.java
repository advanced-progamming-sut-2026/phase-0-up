package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;

// Hypnotizes the zombie that bites the plant, then the plant is consumed (Hypno-shroom).
public class HypnotizeOnEatenAbility extends PlantAbility {
    private double zombieHealthMultiplier = 1.0;
    private double zombieDamageMultiplier = 1.0;

    public HypnotizeOnEatenAbility() {
        super(0, null); // reacts to being eaten, not the tick loop
    }

    // Upgrades (ZOMBIE_HEALTH_MULTIPLIER / ZOMBIE_DAMAGE_MULTIPLIER): buff the hypnotized ally.
    public void setZombieHealthMultiplier(double multiplier) {
        this.zombieHealthMultiplier = multiplier;
    }

    public void setZombieDamageMultiplier(double multiplier) {
        this.zombieDamageMultiplier = multiplier;
    }

    public double getZombieHealthMultiplier() {
        return zombieHealthMultiplier;
    }

    public double getZombieDamageMultiplier() {
        return zombieDamageMultiplier;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        // no tick behavior
    }

    @Override
    public void onOwnerEaten(Plant owner, Zombie eater, GameSession gameSession) {
        eater.getState().setHypnotized(true);
        eater.applyHypnoBuffs(zombieHealthMultiplier, zombieDamageMultiplier);
        if (owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }
}
