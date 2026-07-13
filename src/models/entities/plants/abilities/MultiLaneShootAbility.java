package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.ProjectileType;
import models.entities.projectiles.Trajectory;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Row;
import utils.Constants;

import java.util.List;

public class MultiLaneShootAbility extends PlantAbility {
    private ProjectileType projectileType;
    private int damage;
    private double speed;

    //in which lanes related to plant projectile is getting shot
    //for example for threepeater is {-1, 0, 1}
    private int[] rowOffsets;

    public MultiLaneShootAbility(int actionInterval, TriggerStrategy triggerStrategy, ProjectileType projectileType, int damage, double speed, int[] rowOffsets) {
        super(actionInterval,  triggerStrategy);
        this.projectileType = projectileType;
        this.damage = damage;
        this.speed = speed;
        this.rowOffsets = rowOffsets;
    }


    @Override
    public void execute(Plant owner, GameSession gameSession) {
        for (int offset : rowOffsets) {
            int targetY = owner.getY() + offset;

            if (isValidRow(targetY, gameSession)) {
                Projectile projectile = new Projectile(
                        owner.getX() + 0.5,
                        targetY,
                        projectileType,
                        damage,
                        speed,
                        0,
                        owner,
                        0.0,
                        Element.NEUTRAL,
                        Trajectory.DIRECT
                );

                gameSession.getMap().getRow(targetY).addProjectile(projectile);
            }
        }
    }


    private boolean isValidRow(int y, GameSession session) {
        return y >= 0 && y < Constants.BOARD_ROWS;
    }

    private boolean hasZombieInRow(Row row, double plantX) {
        List<Zombie> zombies = row.getZombies();
        if (zombies != null) {
            for (Zombie z : zombies) {
                if (!z.getHealth().isDead() && z.getMovement().getPositionX() > plantX) {
                    return true;
                }
            }
        }
        return false;
    }
}