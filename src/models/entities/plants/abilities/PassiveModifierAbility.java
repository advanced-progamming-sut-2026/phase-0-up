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

    // Whether the blue flame has been lit. Permanent: a Torchwood stays blue for the rest of its life.
    private boolean blueFlame;

    // Plant food: lights the blue flame (Torchwood).
    public void setDamageMultiplier(int multiplier) {
        this.damageMultiplier = multiplier;
        this.blueFlame = true;
    }

    public boolean isBlueFlame() {
        return blueFlame;
    }

    // Reported as a plant-food boost that never ends, which is exactly what it is: the flame does not
    // wear off, so the view holds the plantfood animation and the glow until the plant is gone. That
    // falls out of the existing rule -- the plant-food loop replays for as long as the model says the
    // boost is running -- rather than needing a special case in the renderer.
    @Override
    public boolean isPlantFoodBusy() {
        return blueFlame;
    }

    // Converts a neutral, direct projectile passing over the plant and scales its damage.
    public void applyTo(Projectile projectile) {
        if (projectile.getTrajectory() == Trajectory.DIRECT && projectile.getElement() == Element.NEUTRAL) {
            projectile.setElement(convertTo);
            projectile.setDamage(projectile.getDamage() * damageMultiplier);
            // Marked on the shot itself rather than expressed as a new Element. A blue pea is a FIRE
            // pea in every rule that matters -- it burns, it melts ice, it is not chilled -- and only
            // its art and its damage differ, so a whole element would have to be taught all of that
            // again everywhere FIRE is already handled.
            projectile.setBlueFlame(blueFlame);
        }
    }
}
