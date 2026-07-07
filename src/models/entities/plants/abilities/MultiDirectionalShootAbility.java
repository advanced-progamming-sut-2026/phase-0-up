package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.DamageType;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.ProjectileType;
import models.game.GameSession;

public class MultiDirectionalShootAbility extends PlantAbility {
    private ProjectileType projectileType;
    private int damage;
    private double[][] directionSpeeds;

    private int shotCount;
    private int remainingShotsInBurst;
    private int burstDelayTicks;
    private int burstTimer;

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
        if (remainingShotsInBurst > 0) return false;

        return super.canExecute(owner, gameSession);
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        if (remainingShotsInBurst > 0) {
            if (burstTimer > 0) {
                burstTimer--;
            } else {
                fireAllDirections(owner, gameSession);
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
        fireAllDirections(owner, gameSession);
        remainingShotsInBurst = shotCount - 1;

        if (remainingShotsInBurst > 0) {
            burstTimer = burstDelayTicks;
        }
    }

    private void fireAllDirections(Plant owner, GameSession gameSession) {
        for (double[] dir : directionSpeeds) {
            Projectile projectile = new Projectile(
                    owner.getX() + 0.5,
                    owner.getY(),
                    projectileType,
                    damage,
                    dir[0],
                    dir[1],
                    owner,
                    0.0,
                    DamageType.STANDARD
            );
            gameSession.getMap().getRow(owner.getY()).addProjectile(projectile);
        }
    }
}