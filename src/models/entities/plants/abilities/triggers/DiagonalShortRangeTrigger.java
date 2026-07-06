package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.GameMap;
import utils.Constants;

public class DiagonalShortRangeTrigger implements TriggerStrategy {
    private double range;

    public DiagonalShortRangeTrigger(double range) {
        this.range = range;
    }

    @Override
    public boolean canTrigger(Plant owner, GameSession gameSession) {
        GameMap map = gameSession.getMap();
        int ownerY = owner.getY();
        double ownerX = owner.getX();

        if (ownerY > 0 && map.getRow(ownerY - 1).getZombies() != null) {
            for (Zombie z : map.getRow(ownerY - 1).getZombies()) {
                if (!z.getHealth().isDead() && Math.abs(z.getMovement().getPositionX() - ownerX) <= range) {
                    return true;
                }
            }
        }

        if (ownerY < Constants.BOARD_ROWS - 1 && map.getRow(ownerY + 1).getZombies() != null) {
            for (Zombie z : map.getRow(ownerY + 1).getZombies()) {
                if (!z.getHealth().isDead() && Math.abs(z.getMovement().getPositionX() - ownerX) <= range) {
                    return true;
                }
            }
        }

        return false;
    }
}
