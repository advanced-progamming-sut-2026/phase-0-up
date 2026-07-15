package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.ProjectileType;
import models.entities.projectiles.Trajectory;
import models.game.GameSession;
import utils.Constants;

public class MultiLaneShootAbility extends PlantAbility implements Burstable {
    private ProjectileType projectileType;
    private int damage;
    private double speed;

    //in which lanes related to plant projectile is getting shot
    //for example for threepeater is {-1, 0, 1}
    private int[] rowOffsets;

    private int remainingShotsInBurst;
    private int burstTimer;
    private boolean plantFoodBurst;
    private static final int BURST_DELAY_TICKS = 3;
    private static final int PLANT_FOOD_BURST_DELAY_TICKS = 1;

    public MultiLaneShootAbility(int actionInterval, TriggerStrategy triggerStrategy, ProjectileType projectileType,
                                 int damage, double speed, int[] rowOffsets) {
        super(actionInterval,  triggerStrategy);
        this.projectileType = projectileType;
        this.damage = damage;
        this.speed = speed;
        this.rowOffsets = rowOffsets;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        if (remainingShotsInBurst > 0) {
            return false;
        }
        return super.canExecute(owner, gameSession);
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        if (remainingShotsInBurst > 0) {
            if (burstTimer > 0) {
                burstTimer--;
            } else {
                fireAllLanes(owner, gameSession);
                remainingShotsInBurst--;
                if (remainingShotsInBurst > 0) {
                    burstTimer = plantFoodBurst ? PLANT_FOOD_BURST_DELAY_TICKS : BURST_DELAY_TICKS;
                }
            }
        }
        super.update(owner, gameSession);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        plantFoodBurst = false;
        fireAllLanes(owner, gameSession);
    }

    @Override
    public void queueBurst(int shots) {
        this.plantFoodBurst = true;
        this.remainingShotsInBurst += shots;
    }

    private void fireAllLanes(Plant owner, GameSession gameSession) {
        for (int offset : rowOffsets) {
            int targetY = owner.getY() + offset;

            if (isValidRow(targetY)) {
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

    private boolean isValidRow(int y) {
        return y >= 0 && y < Constants.BOARD_ROWS;
    }
}
