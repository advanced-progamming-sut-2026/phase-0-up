package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.Trajectory;
import models.game.GameSession;

// Passive projectile modifier applied as shots pass over the plant (Torchwood): boosts neutral direct shots.
public class PassiveModifierAbility extends PlantAbility {
    private Element convertTo;
    private int damageMultiplier;

    public PassiveModifierAbility(Element convertTo, int damageMultiplier) {
        super(0, null);
        this.convertTo = convertTo;
        this.damageMultiplier = damageMultiplier;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        // passive: the effect is applied to passing projectiles via applyTo(...)
    }

    // Converts a neutral, direct projectile passing over the plant and scales its damage.
    public void applyTo(Projectile projectile) {
        if (projectile.getTrajectory() == Trajectory.DIRECT && projectile.getElement() == Element.NEUTRAL) {
            projectile.setElement(convertTo);
            projectile.setDamage(projectile.getDamage() * damageMultiplier);
        }
    }
}
