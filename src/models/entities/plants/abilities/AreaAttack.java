package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;

// Damages every live zombie within a (rowRadius x colRadius) tile area around a plant.
public final class AreaAttack {
    private AreaAttack() { }

    public static void strike(GameSession gameSession, Plant source, int rowRadius, int colRadius,
                              int damage, Element element) {
        for (int rowOffset = -rowRadius; rowOffset <= rowRadius; rowOffset++) {
            int row = source.getY() + rowOffset;
            if (row < 0 || row >= Constants.BOARD_ROWS) continue;

            List<Zombie> zombies = gameSession.getMap().getRow(row).getZombies();
            if (zombies == null) continue;

            for (Zombie z : zombies) {
                if (z.getHealth().isDead()) continue;
                if (Math.abs(z.getMovement().getPositionX() - source.getX()) <= colRadius + 0.5) {
                    z.getHealth().applyDamage(damage, element, source);
                }
            }
        }
    }
}
