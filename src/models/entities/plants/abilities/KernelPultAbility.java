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
    private static final double BASE_BUTTER_CHANCE = 0.25;

    private int kernelDamage;
    private int butterDamage;
    private double speedX;
    private double butterChance = BASE_BUTTER_CHANCE;
    private final Random random = new Random();

    public KernelPultAbility(int actionInterval, TriggerStrategy triggerStrategy, int kernelDamage,
                             int butterDamage, double speedX) {
        super(actionInterval, triggerStrategy);
        this.kernelDamage = kernelDamage;
        this.butterDamage = butterDamage;
        this.speedX = speedX;
    }

    // Upgrade (BUTTER_CHANCE_BUFF): raises the odds of lobbing stunning butter instead of a kernel.
    public void increaseButterChance(double amount) {
        this.butterChance += amount;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        boolean shootButter = random.nextDouble() < butterChance;

        ProjectileType typeToShoot = shootButter ? ProjectileType.BUTTER : ProjectileType.CORN_KERNEL;
        int shootDamage = shootButter ? butterDamage : kernelDamage;
        Element elementToShoot = shootButter ? Element.BUTTER : Element.NEUTRAL;

        Projectile projectile = new Projectile(
                owner.getX() + 0.5,
                owner.getY(),
                typeToShoot,
                shootDamage,
                speedX,
                0.0,
                owner,
                0.0,
                elementToShoot,
                Trajectory.LOBBED);

        gameSession.getMap().getRow(owner.getY()).addProjectile(projectile);
    }
}
