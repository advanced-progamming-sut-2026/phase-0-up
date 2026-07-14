package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.game.GameSession;

// Mine trap: arms over actionInterval, then detonates on contact (see ContactTrigger).
public class DelayedExplosiveAbility extends AreaExplosiveAbility {
    public DelayedExplosiveAbility(int armDelayTicks, TriggerStrategy triggerStrategy, int damage,
                                   int explosionRowRadius, int explosionColRadius, Element element) {
        super(armDelayTicks, triggerStrategy, damage, explosionRowRadius, explosionColRadius, element);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        detonate(owner, gameSession);
    }
}
