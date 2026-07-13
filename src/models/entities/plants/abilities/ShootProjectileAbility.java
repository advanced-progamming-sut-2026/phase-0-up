package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.ProjectileType;
import models.entities.projectiles.Trajectory;
import models.entities.zombies.Zombie;
import models.game.GameSession;

import java.util.List;

public class ShootProjectileAbility extends PlantAbility {
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
    private boolean appliesSlowEffect;

    private int remainingShotsInBurst;
    private int burstDelayTicks;
    private int burstTimer;

    public ShootProjectileAbility(int actionInterval, TriggerStrategy triggerStrategy, ProjectileType projectileType,
                                  int damage, int shotCount, double speed, int burstDelayTicks, int pierceCount,
                                  double maxRange, Element element, Trajectory trajectory,
                                  int splashDamage, double splashRadiusX, int splashRowRadius, boolean appliesSlowEffect) {
        super(actionInterval, triggerStrategy);
        this.projectileType = projectileType;
        this.damage = damage;
        this.shotCount = shotCount;
        this.speedX = speed;
        this.pierceCount = pierceCount;
        this.maxRange = maxRange;
        this.element = element;
        this.trajectory = trajectory;

        this.splashDamage = splashDamage;
        this.splashRadiusX = splashRadiusX;
        this.splashRowRadius = splashRowRadius;
        this.appliesSlowEffect = appliesSlowEffect;

        this.burstDelayTicks = burstDelayTicks;
        this.remainingShotsInBurst = 0;
        this.burstTimer = 0;
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
                fireSingleProjectile(owner, gameSession);
                remainingShotsInBurst--;

                if (remainingShotsInBurst > 0) {
                    burstTimer = burstDelayTicks;
                }
            }
        }

        super.update(owner, gameSession);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {

        fireSingleProjectile(owner, gameSession);

        remainingShotsInBurst = shotCount - 1;

        if (remainingShotsInBurst > 0) {
            burstTimer = burstDelayTicks;
        }
    }

    private void fireSingleProjectile(Plant owner, GameSession gameSession) {
        double spawnX = (direction == ShootDirection.FORWARD) ? owner.getX() + 0.5 : owner.getX() - 0.5;

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
                this.splashRowRadius,
                this.appliesSlowEffect
        );

        gameSession.getMap().getRow(owner.getY()).addProjectile(projectile);
    }

    public void increaseShotCount(int amount) {
        this.shotCount += amount;
    }
}