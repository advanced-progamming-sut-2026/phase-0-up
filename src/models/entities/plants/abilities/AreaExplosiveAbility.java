package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.game.GameSession;

// Base for plants that detonate: deals area burst damage around the plant, then consumes it.
public abstract class AreaExplosiveAbility extends PlantAbility {
    protected int damage;
    protected int explosionRowRadius;
    protected int explosionColRadius;
    protected Element element;

    protected AreaExplosiveAbility(int actionInterval, TriggerStrategy triggerStrategy, int damage,
                                   int explosionRowRadius, int explosionColRadius, Element element) {
        super(actionInterval, triggerStrategy);
        this.damage = damage;
        this.explosionRowRadius = explosionRowRadius;
        this.explosionColRadius = explosionColRadius;
        this.element = element;
    }

    // Upgrade (BONUS_SMASH_CHARGES / BONUS_GRAB_TARGETS): widens the blast so more zombies are caught
    // (Squash crushing two, Tangle Kelp grabbing extra).
    public void widenArea(int extraRowRadius, int extraColRadius) {
        this.explosionRowRadius += extraRowRadius;
        this.explosionColRadius += extraColRadius;
    }

    protected void detonate(Plant owner, GameSession gameSession) {
        AreaAttack.strike(gameSession, owner, explosionRowRadius, explosionColRadius, damage, element);

        // Announce the blast. Every exploding plant funnels through here, so one line covers Cherry
        // Bomb, Jalapeno, Doom-shroom, Potato Mine and the rest.
        //
        // This is also the only signal a renderer can use. A plant being consumed by its own blast is
        // indistinguishable, from the outside, from one being eaten -- both just end up dead and swept
        // on the same tick -- so without saying so here there is no way to know an explosion happened.
        gameSession.reportEvent(owner.getName() + " detonates at ("
                + (int) owner.getX() + ", " + owner.getY() + ")!");

        // the plant is consumed by its own blast
        if (owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }
}
