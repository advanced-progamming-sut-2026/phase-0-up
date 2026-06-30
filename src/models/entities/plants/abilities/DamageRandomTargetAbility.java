package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;

public class DamageRandomTargetAbility extends GlobalTargetingAbility {
    private int damage;

    public DamageRandomTargetAbility(int actionInterval,TargetingPriority priorityStrategy ,double priorityRange, int damage) {
        super(actionInterval, priorityStrategy, priorityRange);
        this.damage = damage;
    }

    @Override
    protected void applyEffectToTarget(Zombie target, Plant owner, GameSession gameSession) {
        target.getHealth().applyDamage(damage, owner);
    }
}
