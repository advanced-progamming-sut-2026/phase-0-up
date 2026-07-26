package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;

// KNOCKBACK_BLAST plant-food: a huge fume cloud that damages every zombie in the lane and shoves
// them back (Fume-shroom).
public class KnockbackBlastStrategy implements PlantFoodStrategy {
    private static final double PUSH_DISTANCE = 2.0;
    private int damage;

    public KnockbackBlastStrategy(int damage) {
        this.damage = damage;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        List<Zombie> zombies = gameSession.getMap().getRow(sourcePlant.getY()).getZombies();
        if (zombies == null) return;

        for (Zombie z : zombies) {
            if (z.isTargetable()) {
                z.getHealth().applyDamage(damage, Element.NEUTRAL, sourcePlant);
                double pushedX = z.getMovement().getPositionX() + PUSH_DISTANCE;
                z.getMovement().setPositionX(Math.min(pushedX, Constants.BOARD_COLS));
            }
        }
    }
}
