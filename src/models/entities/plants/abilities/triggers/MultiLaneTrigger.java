package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.GameMap;
import utils.Constants;

import java.util.List;

public class MultiLaneTrigger implements TriggerStrategy {
    //threepeater: [-1, 0, 1]
    private int[] relativeRows;

    public MultiLaneTrigger(int[] relativeRows) {
        this.relativeRows = relativeRows;
    }

    @Override
    public boolean canTrigger(Plant owner, GameSession gameSession) {
        GameMap map = gameSession.getMap();
        int ownerY = owner.getY();
        double ownerX = owner.getX();

        for (int offset : relativeRows) {
            int targetRow = ownerY + offset;

            if (targetRow >= 0 && targetRow < Constants.BOARD_ROWS) {

                List<Zombie> zombiesInRow = map.getRow(targetRow).getZombies();

                if (zombiesInRow != null) {
                    for (Zombie z : zombiesInRow) {
                        if (z.isTargetable() && z.getMovement().getPositionX() > ownerX) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}