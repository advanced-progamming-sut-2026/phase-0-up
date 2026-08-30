package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.ProjectileType;
import models.entities.projectiles.Trajectory;
import models.game.GameSession;

public class MultiDirectionalShootAbility extends PlantAbility implements Burstable {
    private ProjectileType projectileType;
    private int damage;
    private double[][] directionSpeeds;

    private int shotCount;
    private int remainingShotsInBurst;
    private int burstDelayTicks;
    private int burstTimer;
    private boolean plantFoodBurst;
    private static final int PLANT_FOOD_BURST_DELAY_TICKS = 1;

    public MultiDirectionalShootAbility(int actionInterval, TriggerStrategy triggerStrategy,
                                        ProjectileType projectileType, int damage,
                                        double[][] directionSpeeds,int shotCount) {
        super(actionInterval, triggerStrategy);
        this.projectileType = projectileType;
        this.damage = damage;
        this.directionSpeeds = directionSpeeds;


        this.shotCount = shotCount;

        this.burstDelayTicks = 2;
        this.remainingShotsInBurst = 0;
        this.burstTimer = 0;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        if (remainingShotsInBurst > 0 || windUpRemaining >= 0) return false;

        return super.canExecute(owner, gameSession);
    }

    // The same gap Threepeater had: ROTORUTABAGA ships a 1.73s `attack` clip, the renderer starts an
    // action animation on the rising edge of isWindingUp(), and this ability never reported one -- so
    // the plant never animated and its rutabagas appeared from a motionless plant.
    private static final int WIND_UP_TICKS = 4;
    private int windUpRemaining = -1;

    @Override
    public boolean isWindingUp() {
        return windUpRemaining >= 0;
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        if (windUpRemaining > 0) {
            windUpRemaining--;
        } else if (windUpRemaining == 0) {
            windUpRemaining = -1;
            releaseVolley(owner, gameSession);
        }

        if (remainingShotsInBurst > 0) {
            if (burstTimer > 0) {
                burstTimer--;
            } else {
                fireAllDirections(owner, gameSession);
                remainingShotsInBurst--;

                if (remainingShotsInBurst > 0) {
                    burstTimer = plantFoodBurst ? PLANT_FOOD_BURST_DELAY_TICKS : burstDelayTicks;
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

    // The volley itself, once the draw-back has run out.
    private void releaseVolley(Plant owner, GameSession gameSession) {
        fireAllDirections(owner, gameSession);

        // ADDS this volley's follow-ups; it must never assign over what is already queued -- the same
        // trap ShootProjectileAbility.releaseShot documents, and the reason Rotobaga's plant food did
        // nothing. Feeding it queues forty volleys, and if the plant happened to be mid-wind-up at that
        // moment, the wind-up expired a tick later and this line reset the counter to shotCount - 1,
        // erasing the entire boost. The plant still glowed, because the glow runs off Plant's own
        // two-second floor, so what the player saw was a lit-up Rotobaga firing its ordinary volley.
        boolean wasIdle = remainingShotsInBurst <= 0;
        remainingShotsInBurst += Math.max(0, shotCount - 1);

        if (remainingShotsInBurst > 0 && wasIdle) {
            burstTimer = burstDelayTicks;
        }
    }

    private void fireAllDirections(Plant owner, GameSession gameSession) {
        for (double[] dir : directionSpeeds) {
            Projectile projectile = new Projectile(
                    // The plant's own centre, not half a cell to its right. This ability fires in four
                    // diagonals and the offset was applied to x alone, so the two LEFTWARD rutabagas
                    // were born on the wrong side of the plant and appeared to come out of thin air
                    // beside it. Same reasoning as ShootProjectileAbility.fireSingleProjectile.
                    owner.getX(),
                    owner.getY(),
                    projectileType,
                    damage,
                    dir[0],
                    dir[1],
                    owner,
                    0.0,
                    Element.NEUTRAL,
                    Trajectory.DIRECT
            );
            gameSession.getMap().getRow(owner.getY()).addProjectile(projectile);
        }
    }

    @Override
    public void queueBurst(int shots) {
        this.plantFoodBurst = true;
        this.remainingShotsInBurst += shots;
    }

    // The flag as well as the count: this same counter carries the ordinary multi-shot cadence, and a
    // Rotobaga firing its normal volley is not a plant under plant food.
    @Override
    public boolean isPlantFoodBusy() {
        return plantFoodBurst && remainingShotsInBurst > 0;
    }
}