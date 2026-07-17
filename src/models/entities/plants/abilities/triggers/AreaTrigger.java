package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.GameMap;
import utils.Constants;

import java.util.List;

// Fires when a live zombie is within a (rowRadius x colRadius) tile area around the plant (melee reach).
public class AreaTrigger implements TriggerStrategy {
    private int rowRadius;
    private int colRadius;

    public AreaTrigger(int rowRadius, int colRadius) {
        this.rowRadius = rowRadius;
        this.colRadius = colRadius;
    }

    @Override
    public boolean canTrigger(Plant owner, GameSession gameSession) {
        GameMap map = gameSession.getMap();

        for (int rowOffset = -rowRadius; rowOffset <= rowRadius; rowOffset++) {
            int row = owner.getY() + rowOffset;
            if (row < 0 || row >= Constants.BOARD_ROWS) continue;

            List<Zombie> zombies = map.getRow(row).getZombies();
            if (zombies == null) continue;

            for (Zombie z : zombies) {
                if (z.isTargetable()
                        && Math.abs(z.getMovement().getPositionX() - owner.getX()) <= colRadius + 0.5) {
                    return true;
                }
            }
        }
        return false;
    }
}
