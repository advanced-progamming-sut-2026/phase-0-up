package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.game.GameSession;

// Base for plants that detonate: deals area burst damage around the plant, then consumes it.
public abstract class AreaExplosiveAbility extends PlantAbility {
    protected int damage;
    protected int explosionRowRadius;
    protected int explosionColRadius;
    protected Element element;

    protected AreaExplosiveAbility(int actionInterval, TriggerStrategy triggerStrategy, int damage,
                                   int explosionRowRadius, int explosionColRadius, Element element) {
        super(actionInterval, triggerStrategy);
        this.damage = damage;
        this.explosionRowRadius = explosionRowRadius;
        this.explosionColRadius = explosionColRadius;
        this.element = element;
    }

    protected void detonate(Plant owner, GameSession gameSession) {
        AreaAttack.strike(gameSession, owner, explosionRowRadius, explosionColRadius, damage, element);

        // the plant is consumed by its own blast
        if (owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }
}
