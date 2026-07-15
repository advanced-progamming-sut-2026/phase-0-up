package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;

// Freezes every zombie on the board once on placement, then is consumed (Ice-shroom).
public class InstantFreezeAbility extends PlantAbility {
    private int freezeDurationTicks;
    private boolean hasExecuted;

    public InstantFreezeAbility(int freezeDurationTicks) {
        super(0, null);
        this.freezeDurationTicks = freezeDurationTicks;
        this.hasExecuted = false;
    }

    // Upgrade (FREEZE_DURATION_EXT): keeps the board-wide freeze in effect longer (Ice-shroom).
    public void extendFreeze(int ticks) {
        this.freezeDurationTicks += ticks;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        return !hasExecuted;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        hasExecuted = true;

        for (int row = 0; row < Constants.BOARD_ROWS; row++) {
            List<Zombie> zombies = gameSession.getMap().getRow(row).getZombies();
            if (zombies == null) continue;

            for (Zombie z : zombies) {
                if (!z.getHealth().isDead()) {
                    z.getState().applyFreeze(freezeDurationTicks);
                }
            }
        }

        if (owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }
}
