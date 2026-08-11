package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.ProjectileType;
import models.entities.projectiles.Trajectory;
import models.game.GameSession;


public class ShootProjectileAbility extends PlantAbility implements Burstable {
    private ProjectileType projectileType;
    private int damage;
    private int shotCount;
    private double speedX;
    private ShootDirection direction;
    private int pierceCount;
    private double maxRange;
    private Element element;
    private Trajectory trajectory;

    //splash properties
    private int splashDamage;
    private double splashRadiusX;
    private int splashRowRadius;

    // Ticks the plant spends visibly winding up before the shot actually leaves. The projectile is
    // created when this expires, so a renderer can play the attack animation THROUGH the wind-up and
    // have it finish exactly as the pea appears.
    //
    // This does not change the firing RATE: PlantAbility resets cooldownTimer to actionInterval when
    // execute() runs, so the cycle length is untouched and only the moment within it shifts. DPS is
    // the same; the first shot of a plant's life lands WIND_UP_TICKS later.
    private static final int WIND_UP_TICKS = 4;   // 0.4s at 10 ticks/sec
    private int windUpRemaining = -1;             // -1 = not winding up

    private int remainingShotsInBurst;
    private int burstDelayTicks;
    private int burstTimer;
    private boolean plantFoodBurst;
    private static final int PLANT_FOOD_BURST_DELAY_TICKS = 1;

    // plant food: staggered giant shots
    private int pendingGiantShots;
    private int giantShotTimer;
    private int giantShotMultiplier = 1;
    private static final int GIANT_SHOT_DELAY_TICKS = 5;

    // status effects carried by each shot (Snow Pea chill extension, Goo poison-over-time)
    private int chillBonusTicks;
    private int poisonDps;
    private static final int BASE_POISON_DPS = 10;
    private static final int POISON_DURATION_TICKS = 50;

    public ShootProjectileAbility(int actionInterval, TriggerStrategy triggerStrategy, ProjectileType projectileType,
                                  int damage, int shotCount, double speed, int burstDelayTicks, int pierceCount,
                                  double maxRange, Element element, Trajectory trajectory, ShootDirection direction,
                                  int splashDamage, double splashRadiusX, int splashRowRadius) {
        super(actionInterval, triggerStrategy);
        this.projectileType = projectileType;
        this.damage = damage;
        this.shotCount = shotCount;
        this.speedX = speed;
        this.pierceCount = pierceCount;
        this.maxRange = maxRange;
        this.element = element;
        this.trajectory = trajectory;
        this.direction = direction;

        this.splashDamage = splashDamage;
        this.splashRadiusX = splashRadiusX;
        this.splashRowRadius = splashRowRadius;

        this.burstDelayTicks = burstDelayTicks;
        this.remainingShotsInBurst = 0;
        this.burstTimer = 0;

        // poison shooters (Goo Peashooter) inflict a damage-over-time by default
        this.poisonDps = (element == Element.POISON) ? BASE_POISON_DPS : 0;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        if (remainingShotsInBurst > 0 || windUpRemaining >= 0) {
            return false;   // already committed to a shot
        }
        return super.canExecute(owner, gameSession);
    }

    // True while the plant is drawing back for a shot that has not been fired yet. The view reads this
    // to know which animation to play; the model needs it for nothing else.
    @Override
    public boolean isWindingUp() {
        return windUpRemaining >= 0;
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        // The wind-up started by execute() runs down here, and the shot leaves on the tick it ends.
        if (windUpRemaining > 0) {
            windUpRemaining--;
        } else if (windUpRemaining == 0) {
            windUpRemaining = -1;
            releaseShot(owner, gameSession);
        }

        if (remainingShotsInBurst > 0) {
            if (burstTimer > 0) {
                burstTimer--;
            } else {
                fireSingleProjectile(owner, gameSession);
                remainingShotsInBurst--;

                if (remainingShotsInBurst > 0) {
                    burstTimer = plantFoodBurst ? PLANT_FOOD_BURST_DELAY_TICKS : burstDelayTicks;
                }
            }
        }

        updateGiantShots(owner, gameSession);

        super.update(owner, gameSession);
    }

    // Begins the shot. The projectile itself is created in releaseShot once the wind-up elapses.
    @Override
    public void execute(Plant owner, GameSession gameSession) {
        plantFoodBurst = false;
        windUpRemaining = WIND_UP_TICKS;
    }

    // The wind-up has finished: the shot leaves now.
    private void releaseShot(Plant owner, GameSession gameSession) {
        fireSingleProjectile(owner, gameSession);

        remainingShotsInBurst = shotCount - 1;

        if (remainingShotsInBurst > 0) {
            burstTimer = burstDelayTicks;
        }
    }

    private void fireSingleProjectile(Plant owner, GameSession gameSession) {
        // Spawned AT the plant, not half a cell ahead of it.
        //
        // The old +0.5 offset put the shot past any zombie standing closer than half a tile -- which is
        // exactly where a zombie sits while eating this plant. The pea appeared behind its target and
        // flew off harmlessly, so a shooter under attack could never defend itself and looked like it
        // had simply stopped firing. Projectile.handleZombieCollisions sweeps from the previous x to
        // the current one, so starting on the plant's own tile lets the very first step register the
        // hit. Firing backwards keeps its offset: that shot travels away from the plant's own tile.
        double spawnX = (direction == ShootDirection.FORWARD) ? owner.getX() : owner.getX() - 0.5;

        Projectile projectile = new Projectile(
                spawnX,
                owner.getY(),
                projectileType,
                damage,
                speedX,
                0,
                owner,
                maxRange,
                element,
                trajectory
        );

        projectile.setPierceCount(pierceCount);

        projectile.setSplashProperties(
                this.splashDamage,
                this.splashRadiusX,
                this.splashRowRadius
        );

        if (chillBonusTicks > 0) {
            projectile.setChillBonusTicks(chillBonusTicks);
        }
        if (poisonDps > 0) {
            projectile.setPoison(poisonDps, POISON_DURATION_TICKS);
        }

        gameSession.getMap().getRow(owner.getY()).addProjectile(projectile);
    }

    public Element getElement() {
        return element;
    }

    public void increaseShotCount(int amount) {
        this.shotCount += amount;
    }

    // Upgrade (CHILL_DURATION_EXT): each ice shot chills for longer (Snow Pea).
    public void increaseChillDuration(int ticks) {
        this.chillBonusTicks += ticks;
    }

    // Upgrade (POISON_TICK_BUFF): stronger poison-over-time per second (Goo Peashooter).
    public void increasePoisonDps(int amount) {
        this.poisonDps += amount;
    }

    // Upgrade (ADDITIONAL_PIERCE): shots pass through more zombies (Cactus).
    public void increasePierce(int amount) {
        this.pierceCount += amount;
    }

    // Upgrade (TILE_RANGE_EXT): extends how far short-range shots travel (Sea/Puff/Fume-shroom).
    public void increaseMaxRange(double tiles) {
        if (this.maxRange > 0.0) {
            this.maxRange += tiles;
        }
    }

    // Upgrade (SPLASH_DAMAGE_BUFF): boosts the area splash of lobbed melons.
    public void increaseSplashDamage(int amount) {
        this.splashDamage += amount;
    }

    @Override
    public void queueBurst(int shots) {
        this.plantFoodBurst = true;
        this.remainingShotsInBurst += shots;
    }

    // Plant food: queues `count` giant shots (boosted damage), fired one at a time with a delay.
    public void queueGiantShots(int count, int damageMultiplier) {
        this.pendingGiantShots += count;
        this.giantShotMultiplier = damageMultiplier;
    }

    private void updateGiantShots(Plant owner, GameSession gameSession) {
        if (pendingGiantShots <= 0) return;

        if (giantShotTimer > 0) {
            giantShotTimer--;
            return;
        }

        int originalDamage = this.damage;
        this.damage = originalDamage * giantShotMultiplier;
        fireSingleProjectile(owner, gameSession);
        this.damage = originalDamage;

        pendingGiantShots--;
        if (pendingGiantShots > 0) {
            giantShotTimer = GIANT_SHOT_DELAY_TICKS;
        }
    }

    // Plant food: permanently upgrades this shooter's projectiles (Cactus electric thorns).
    public void upgradeToElectric(int damageMultiplier, int pierceCount) {
        this.projectileType = ProjectileType.PIERCING_SPIKE;
        this.damage *= damageMultiplier;
        this.pierceCount = pierceCount;
    }

    // Plant food: lobs one of this plant's projectiles into the given lane (lobber barrage at random zombies).
    public void lobInLane(Plant owner, GameSession gameSession, int targetRow) {
        // Same reasoning as fireSingleProjectile: start on the plant's own tile so a zombie already
        // standing on top of it is still in front of the shot.
        Projectile projectile = new Projectile(
                owner.getX(), targetRow, projectileType, damage, speedX, 0, owner, maxRange, element, trajectory);
        projectile.setPierceCount(pierceCount);
        projectile.setSplashProperties(splashDamage, splashRadiusX, splashRowRadius);
        gameSession.getMap().getRow(targetRow).addProjectile(projectile);
    }
}