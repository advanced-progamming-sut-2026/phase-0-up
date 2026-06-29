package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.ProjectileType;
import models.entities.zombies.Zombie;
import models.game.GameSession;

import java.util.List;

public class ShootProjectileAbility extends PlantAbility {
    private ProjectileType projectileType;
    private int damage;
    private int shotCount;
    private double speed;

    private int remainingShotsInBurst;
    private int burstDelayTicks;
    private int burstTimer;

    public ShootProjectileAbility(int actionInterval, ProjectileType projectileType, int damage, int shotCount, double speed) {
        super(actionInterval);
        this.projectileType = projectileType;
        this.damage = damage;
        this.shotCount = shotCount;
        this.speed = speed;


        this.burstDelayTicks = 2;
        this.remainingShotsInBurst = 0;
        this.burstTimer = 0;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        if (remainingShotsInBurst > 0) {
            return false;
        }

        List<Zombie> zombiesInRow = gameSession.getMap().getRow(owner.getY()).getZombies();

        if (zombiesInRow != null) {
            for (Zombie z : zombiesInRow) {
                if (!z.getHealth().isDead() && z.getMovement().getPositionX() > owner.getX()) {
                    return true;
                }
            }
        }
        return false;
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
        double spawnX = owner.getX() + 0.5;

        Projectile projectile = new Projectile(
                spawnX,
                owner.getY(),
                projectileType,
                damage,
                speed,
                owner
        );

        gameSession.getMap().getRow(owner.getY()).addProjectile(projectile);
    }
}