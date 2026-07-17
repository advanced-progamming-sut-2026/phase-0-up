package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.GameMap;
import utils.Constants;

import java.util.List;

public class GlobalTrigger implements TriggerStrategy {

    @Override
    public boolean canTrigger(Plant owner, GameSession gameSession) {
        GameMap map = gameSession.getMap();

        for (int i = 0; i < Constants.BOARD_ROWS; i++) {
            List<Zombie> zombies = map.getRow(i).getZombies();
            if (zombies != null) {
                for (Zombie z : zombies) {
                    if (z.isTargetable()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
