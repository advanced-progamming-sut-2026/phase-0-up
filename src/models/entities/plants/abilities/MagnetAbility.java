package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;

// Pulls metallic armor (bucket, helmet, shoulder) off the nearest armored zombie in range (Magnet-shroom).
public class MagnetAbility extends PlantAbility {
    private double range;

    public MagnetAbility(int actionInterval, TriggerStrategy triggerStrategy, double range) {
        super(actionInterval, triggerStrategy);
        this.range = range;
    }

    // Upgrade (TILE_RANGE_EXT): reaches further to strip metal armour (Magnet-shroom).
    public void increaseRange(double tiles) {
        this.range += tiles;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        Zombie target = nearestMetalInRange(owner, gameSession);
        if (target != null) {
            target.getHealth().tryRemoveMetallicArmor();
        }
    }

    private Zombie nearestMetalInRange(Plant owner, GameSession gameSession) {
        Zombie nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (int row = 0; row < Constants.BOARD_ROWS; row++) {
            List<Zombie> zombies = gameSession.getMap().getRow(row).getZombies();
            if (zombies == null) continue;

            for (Zombie z : zombies) {
                if (!z.isTargetable() || !hasMetalOnTop(z)) continue;

                double dx = z.getMovement().getPositionX() - owner.getX();
                double dy = row - owner.getY();
                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance <= range && distance < minDistance) {
                    minDistance = distance;
                    nearest = z;
                }
            }
        }
        return nearest;
    }

    private boolean hasMetalOnTop(Zombie z) {
        ArmorType top = z.getHealth().getLayers().peek().getType();
        return top == ArmorType.BUCKET || top == ArmorType.SHOULDER_ARMOR || top == ArmorType.CROWN;
    }
}
