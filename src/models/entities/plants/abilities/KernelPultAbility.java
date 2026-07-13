package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.ProjectileType;
import models.entities.projectiles.Trajectory;
import models.game.GameSession;

import java.util.Random;

public class KernelPultAbility extends PlantAbility{
    private int kernelDamage;
    private int butterDamage;
    private double speedX;
    private Random random;

    public KernelPultAbility(int actionInterval, TriggerStrategy triggerStrategy, int kernelDamage,
                             int butterDamage, double speedX) {
        super(actionInterval, triggerStrategy);
        this.kernelDamage = kernelDamage;
        this.butterDamage = butterDamage;
        this.speedX = speedX;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        boolean shootButter = random.nextDouble() < 0.25;

        ProjectileType typeToShoot = shootButter ? ProjectileType.BUTTER : ProjectileType.CORN_KERNEL;
        int shootDamage = shootButter ? butterDamage : kernelDamage;

        Projectile projectile = new Projectile(
                owner.getX() + 0.5,
                owner.getY(),
                typeToShoot,
                shootDamage,
                speedX,
                0.0,
                owner,
                0.0,
                Element.NEUTRAL,
                Trajectory.LOBBED);

        gameSession.getMap().getRow(owner.getY()).addProjectile(projectile);
    }
}
