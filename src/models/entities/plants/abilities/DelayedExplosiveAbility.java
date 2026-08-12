package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.game.GameSession;

// Mine trap: arms over actionInterval, then detonates on contact (see ContactTrigger).
public class DelayedExplosiveAbility extends AreaExplosiveAbility {

    // Whether the mine has finished burying itself and is live.
    //
    // The arm delay IS the ability's actionInterval, so the countdown already exists -- it just had no
    // way out. The view needs it because an unarmed mine and an armed one are different animations
    // (plant_idle, a small lump in the dirt, versus idle, the whole potato with its eyes open), and
    // drawing the armed pose the whole time is what made a buried mine look ready to go off.
    public boolean isArmed() {
        return cooldownTimer <= 0;
    }
    public DelayedExplosiveAbility(int armDelayTicks, TriggerStrategy triggerStrategy, int damage,
                                   int explosionRowRadius, int explosionColRadius, Element element) {
        super(armDelayTicks, triggerStrategy, damage, explosionRowRadius, explosionColRadius, element);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        detonate(owner, gameSession);
    }

    // Plant food: skips the arm delay so the mine is immediately live.
    public void armInstantly() {
        this.cooldownTimer = 0;
    }
}
