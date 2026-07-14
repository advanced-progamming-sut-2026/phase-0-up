package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.game.GameSession;

// Detonates when the owning plant dies (Explode-o-nut); a passive wall until then.
public class DeathExplosiveAbility extends AreaExplosiveAbility {
    public DeathExplosiveAbility(int damage, int explosionRowRadius, int explosionColRadius, Element element) {
        super(0, null, damage, explosionRowRadius, explosionColRadius, element);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        // passive until death
    }

    @Override
    public void onOwnerDeath(Plant owner, GameSession gameSession) {
        detonate(owner, gameSession);
    }
}
