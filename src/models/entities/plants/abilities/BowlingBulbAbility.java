package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.ProjectileType;
import models.entities.zombies.Zombie;
import models.game.GameSession;

import java.util.List;

public class BowlingBulbAbility extends PlantAbility {

    private int cyanDamage = 40;
    private int blueDamage = 120;
    private int orangeDamage = 180;

    private int cyanReloadTicks;
    private int blueReloadTicks;
    private int orangeReloadTicks;

    private int cyanTimer = 0;
    private int blueTimer = 0;
    private int orangeTimer = 0;

    private boolean hasCyan = true;
    private boolean hasBlue = true;
    private boolean hasOrange = true;

    public BowlingBulbAbility(int actionInterval, TriggerStrategy triggerStrategy,
                              int cyanReloadTicks, int blueReloadTicks, int orangeReloadTicks) {
        super(actionInterval, triggerStrategy);
        this.cyanReloadTicks = cyanReloadTicks;
        this.blueReloadTicks = blueReloadTicks;
        this.orangeReloadTicks = orangeReloadTicks;
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        if (!hasCyan) {
            cyanTimer++;
            if (cyanTimer >= cyanReloadTicks) {
                hasCyan = true;
                cyanTimer = 0;
            }
        }

        if (!hasBlue) {
            blueTimer++;
            if (blueTimer >= blueReloadTicks) {
                hasBlue = true;
                blueTimer = 0;
            }
        }

        if (!hasOrange) {
            orangeTimer++;
            if (orangeTimer >= orangeReloadTicks) {
                hasOrange = true;
                orangeTimer = 0;
            }
        }

        super.update(owner, gameSession);
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        if (!hasCyan && !hasBlue && !hasOrange) return false;

        return super.canExecute(owner, gameSession);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        int damageToShoot = 0;

        if (hasCyan) {
            damageToShoot = cyanDamage;
            hasCyan = false;
        } else if (hasBlue) {
            damageToShoot = blueDamage;
            hasBlue = false;
        } else if (hasOrange) {
            damageToShoot = orangeDamage;
            hasOrange = false;
        } else {
            return;
        }

        Projectile projectile = new Projectile(
                owner.getX() + 0.5,
                owner.getY(),
                ProjectileType.BOWLING_BULB,
                damageToShoot,
                1.0,
                0.0,
                owner
        );

        projectile.setBounceCount(3);
        gameSession.getMap().getRow(owner.getY()).addProjectile(projectile);
    }
}
