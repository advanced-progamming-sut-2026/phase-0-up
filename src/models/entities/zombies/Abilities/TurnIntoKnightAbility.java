package models.entities.zombies.Abilities;

import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Components.HealthLayer;
import models.entities.zombies.Zombie;
import models.map.Row;

public class TurnIntoKnightAbility implements ZombieAbility {

    private int tickCounter = 0;
    private static final int TICKS_PER_SECOND = 10;
    private static final int KNIGHT_COOLDOWN = 5 * TICKS_PER_SECOND;

    @Override
    public void execute(Zombie king) {
        if (king == null || king.getState().isUnableToMove()) {
            return;
        }

        tickCounter++;
        if (tickCounter >= KNIGHT_COOLDOWN) {
            Zombie target = findSimpleZombieNearby(king);

            if (target != null) {
                turnIntoKnight(target);
                king.getGameSession().reportEvent("The King Zombie knights a peasant zombie at ("
                        + (int) target.getX() + ", " + target.getY() + "), granting it a crown and shoulder armor.");
                tickCounter = 0;
            }
        }
    }

    private Zombie findSimpleZombieNearby(Zombie king) {
        if (king.getGameSession() == null || king.getGameSession().getMap() == null) {
            return null;
        }

        int kingRow = king.getMovement().getPositionY();

        for (int r = kingRow - 1; r <= kingRow + 1; r++) {
            if (r >= 0 && r < 5) {
                Row row = king.getGameSession().getMap().getRow(r);

                if (row != null && row.getZombies() != null) {
                    for (Zombie z : row.getZombies()) {
                        if (z != king && !z.getHealth().isDead() && !z.getState().isHypnotized()) {
                            if (isSimpleZombie(z)) {
                                return z;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private boolean isSimpleZombie(Zombie zombie) {
        if (zombie.getHealth() == null) return false;
        return !zombie.getHealth().hasArmor();
    }

    private void turnIntoKnight(Zombie target) {
        if (target.getHealth() == null) return;
        target.getHealth().addLayer(new HealthLayer(ArmorType.CROWN));
        target.getHealth().addLayer(new HealthLayer(ArmorType.SHOULDER_ARMOR));
    }
}