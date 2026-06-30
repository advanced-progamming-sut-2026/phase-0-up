package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;

public class HypnotizeRandomTarget extends GlobalTargetingAbility{

    public HypnotizeRandomTarget(int actionInterval, TargetingPriority priorityStrategy, double priorityRange) {
        super(actionInterval, priorityStrategy, priorityRange);
    }

    @Override
    protected void applyEffectToTarget(Zombie target, Plant owner, GameSession gameSession) {
        if (!target.isHypnotized()){
            target.applyHypnotize();
        }
    }
}
