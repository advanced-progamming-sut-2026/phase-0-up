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

    // Says what it did.
    //
    // It used to do this in total silence: no event, no sound, nothing. The armour does come off --
    // ZombieRenderer watches the layer stack and throws the piece clear when it leaves -- but a pull
    // happens once every actionInterval (ten seconds for Magnet-shroom) and only within `range` tiles,
    // so a player watching for it has no way to tell a magnet that is working from one that is not.
    // That is the whole of "I don't see it working": the effect was real and unannounced.
    @Override
    public void execute(Plant owner, GameSession gameSession) {
        Zombie target = nearestMetalInRange(owner, gameSession);
        if (target == null) {
            return;
        }
        String pulled = target.getHealth().getLayers().peek().getType().getDisplayName();
        if (!target.getHealth().tryRemoveMetallicArmor()) {
            return;
        }
        gameSession.reportEvent(owner.getName() + " yanks the " + pulled + " off a "
                + target.getAlias() + " in lane " + target.getMovement().getPositionY() + "!");
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

    // The armour stack is a java.util.Stack, and Stack.peek() THROWS on an empty one -- it does not
    // return null. Every zombie without armour has an empty stack, which is most of them, so this
    // asked the question in a way that could only be answered for the zombies it was already looking
    // for: an EmptyStackException out of the middle of CombatSystem's plant pass the first time a
    // Magnet-shroom ticked with an ordinary browncoat on the lawn. The magnet did nothing from then on,
    // and so did anything the pass had not reached yet.
    //
    // HealthComponent.tryRemoveMetallicArmor -- which this method exists to predict -- has always had
    // the isEmpty() guard. The two had simply drifted apart.
    private boolean hasMetalOnTop(Zombie z) {
        java.util.Stack<models.entities.zombies.Components.HealthLayer> layers =
                z.getHealth().getLayers();
        if (layers == null || layers.isEmpty()) {
            return false;
        }
        ArmorType top = layers.peek().getType();
        return top == ArmorType.BUCKET || top == ArmorType.SHOULDER_ARMOR || top == ArmorType.CROWN;
    }
}
