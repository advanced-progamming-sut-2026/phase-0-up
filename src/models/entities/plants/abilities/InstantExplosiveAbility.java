package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.game.GameSession;

// Detonates once immediately on placement, then is consumed (Cherry Bomb, Jalapeno, Doom-shroom, ...).
public class InstantExplosiveAbility extends AreaExplosiveAbility {
    private boolean hasExecuted;

    public InstantExplosiveAbility(int damage, int explosionRowRadius, int explosionColRadius, Element element) {
        super(0, null, damage, explosionRowRadius, explosionColRadius, element);
        this.hasExecuted = false;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        return !hasExecuted;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        hasExecuted = true;
        detonate(owner, gameSession);
    }
}
