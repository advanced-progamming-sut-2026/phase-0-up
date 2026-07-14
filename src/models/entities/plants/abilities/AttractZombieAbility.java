package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;

// Pulls zombies from adjacent lanes into the plant's own lane (Sweet Potato).
public class AttractZombieAbility extends PlantAbility {
    public AttractZombieAbility(int actionInterval, TriggerStrategy triggerStrategy) {
        super(actionInterval, triggerStrategy);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        int ownLane = owner.getY();
        pullInto(gameSession, ownLane - 1, ownLane);
        pullInto(gameSession, ownLane + 1, ownLane);
    }

    private void pullInto(GameSession gameSession, int fromLane, int toLane) {
        if (fromLane < 0 || fromLane >= Constants.BOARD_ROWS) return;

        List<Zombie> zombies = gameSession.getMap().getRow(fromLane).getZombies();
        if (zombies == null) return;

        for (Zombie z : zombies) {
            if (!z.getHealth().isDead()) {
                z.getMovement().startLaneSwitch(toLane);
            }
        }
    }
}
