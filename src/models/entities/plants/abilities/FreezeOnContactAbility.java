package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.zombies.Zombie;
import models.game.GameSession;

import java.util.List;

// Trap: freezes zombies on the plant's tile solid, then is consumed (Iceberg Lettuce).
public class FreezeOnContactAbility extends PlantAbility {
    private int freezeDurationTicks;

    public FreezeOnContactAbility(int actionInterval, TriggerStrategy triggerStrategy, int freezeDurationTicks) {
        super(actionInterval, triggerStrategy);
        this.freezeDurationTicks = freezeDurationTicks;
    }

    // Upgrade (FREEZE_DURATION_EXT): keeps stepped-on zombies frozen longer (Iceberg Lettuce).
    public void extendFreeze(int ticks) {
        this.freezeDurationTicks += ticks;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        List<Zombie> zombies = gameSession.getMap().getRow(owner.getY()).getZombies();
        if (zombies != null) {
            for (Zombie z : zombies) {
                if (!z.getHealth().isDead()
                        && Math.abs(z.getMovement().getPositionX() - owner.getX()) <= 0.5) {
                    z.getState().applyFreeze(freezeDurationTicks);
                }
            }
        }

        if (owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }
}
