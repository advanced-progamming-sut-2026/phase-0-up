package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.zombies.Zombie;
import models.game.GameSession;

public class HypnotizeRandomTargetAbility extends GlobalTargetingAbility{

    public HypnotizeRandomTargetAbility(int actionInterval, TriggerStrategy triggerStrategy, TargetingPriority priorityStrategy, double priorityRange) {
        super(actionInterval, triggerStrategy, priorityStrategy, priorityRange);
    }

    @Override
    protected void applyEffectToTarget(Zombie target, Plant owner, GameSession gameSession) {
        if (!target.getState().isHypnotized()){
            target.getState().setHypnotized(true);
        }
    }
}
