package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;

// Mine trap: arms over actionInterval, then detonates on contact dealing area burst damage and is consumed.
public class DelayedExplosiveAbility extends PlantAbility {
    private int damage;
    private int explosionRowRadius;
    private int explosionColRadius;
    private Element element;

    public DelayedExplosiveAbility(int armDelayTicks, TriggerStrategy triggerStrategy, int damage,
                                   int explosionRowRadius, int explosionColRadius, Element element) {
        super(armDelayTicks, triggerStrategy);
        this.damage = damage;
        this.explosionRowRadius = explosionRowRadius;
        this.explosionColRadius = explosionColRadius;
        this.element = element;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        detonate(owner, gameSession);

        // mine is consumed on explosion
        if (owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }

    private void detonate(Plant owner, GameSession gameSession) {
        for (int rowOffset = -explosionRowRadius; rowOffset <= explosionRowRadius; rowOffset++) {
            int row = owner.getY() + rowOffset;
            if (row < 0 || row >= Constants.BOARD_ROWS) continue;

            List<Zombie> zombies = gameSession.getMap().getRow(row).getZombies();
            if (zombies == null) continue;

            for (Zombie z : zombies) {
                if (z.getHealth().isDead()) continue;

                double distanceX = Math.abs(z.getMovement().getPositionX() - owner.getX());
                if (distanceX <= explosionColRadius + 0.5) {
                    z.getHealth().applyDamage(damage, element, owner);
                }
            }
        }
    }
}
