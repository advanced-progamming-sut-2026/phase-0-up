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
        if (remainingShotsInBurst > 0 || windUpRemaining >= 0) {
            return false;   // already committed to a volley
        }
        return super.canExecute(owner, gameSession);
    }

    // Ticks the plant spends visibly drawing back before the volley leaves. Threepeater ships a
    // one-second `attack` clip and nothing ever played it: the renderer starts an action animation on
    // the rising edge of isWindingUp(), and this ability never reported one, so the plant sat in its
    // idle pose while peas appeared in three lanes out of nowhere.
    //
    // Costs the simulation nothing -- PlantAbility still resets the cooldown to actionInterval when
    // execute() runs, so only the moment within the cycle shifts. Same trade as
    // ShootProjectileAbility.WIND_UP_TICKS.
    private static final int WIND_UP_TICKS = 4;
    private int windUpRemaining = -1;

    @Override
    public boolean isWindingUp() {
        return windUpRemaining >= 0;
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        // The draw-back started by execute() runs down here; the volley leaves on the tick it ends.
        if (windUpRemaining > 0) {
            windUpRemaining--;
        } else if (windUpRemaining == 0) {
            windUpRemaining = -1;
            fireAllLanes(owner, gameSession);
        }

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
        windUpRemaining = WIND_UP_TICKS;
    }

    @Override
    public void queueBurst(int shots) {
        this.plantFoodBurst = true;
        this.remainingShotsInBurst += shots;
    }

    // The flag as well as the count: this same counter carries the ordinary multi-shot cadence, and a
    // Threepeater firing its normal volley is not a plant under plant food.
    @Override
    public boolean isPlantFoodBusy() {
        return plantFoodBurst && remainingShotsInBurst > 0;
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
