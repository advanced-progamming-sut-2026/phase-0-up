package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.DamageType;
import models.entities.zombies.Zombie;
import models.game.GameSession;

public class DamageRandomTargetAbility extends GlobalTargetingAbility {
    private int damage;

    public DamageRandomTargetAbility(int actionInterval, TriggerStrategy triggerStrategy, TargetingPriority priorityStrategy , double priorityRange, int damage) {
        super(actionInterval, triggerStrategy, priorityStrategy, priorityRange);
        this.damage = damage;
    }

    @Override
    protected void applyEffectToTarget(Zombie target, Plant owner, GameSession gameSession) {
        target.getHealth().applyDamage(damage, DamageType.STANDARD, owner);
    }
}
