package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.ProjectileType;
import models.entities.projectiles.Trajectory;
import models.game.GameSession;

import java.util.Random;

public class KernelPultAbility extends PlantAbility implements VariantAction {
    private static final double BASE_BUTTER_CHANCE = 0.25;

    private int kernelDamage;
    private int butterDamage;
    private double speedX;
    private double butterChance = BASE_BUTTER_CHANCE;
    private final Random random = new Random();

    // Which of the two throws was last made. The art has a separate swing for the butter (attack2), and
    // only this ability knows which one it just lobbed -- the choice is a coin flip inside execute().
    private boolean lastWasButter;

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

    // Ticks spent winding up before the lob leaves. Without one the plant had no attack animation at
    // all: the renderer starts an action clip on the rising edge of isWindingUp(), and this ability
    // never reported one -- so KERNALPULT's `attack` and `attack2` (the kernel swing and the butter
    // swing) were never played and the corn just stood there while kernels appeared out of nowhere.
    //
    // Which of the two plays is already answered by actionVariant() below; the roll happens when the
    // swing STARTS so the view picks the right arm on the first frame of it.
    private static final int WIND_UP_TICKS = 4;
    private int windUpRemaining = -1;

    @Override
    public boolean isWindingUp() {
        return windUpRemaining >= 0;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        if (windUpRemaining >= 0) {
            return false;   // already mid-swing
        }
        return super.canExecute(owner, gameSession);
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        if (windUpRemaining > 0) {
            windUpRemaining--;
        } else if (windUpRemaining == 0) {
            windUpRemaining = -1;
            lob(owner, gameSession);
        }
        super.update(owner, gameSession);
    }

    // Rolls which of the two it is about to throw and begins the swing; the shot leaves in lob().
    @Override
    public void execute(Plant owner, GameSession gameSession) {
        lastWasButter = random.nextDouble() < butterChance;
        windUpRemaining = WIND_UP_TICKS;
    }

    private void lob(Plant owner, GameSession gameSession) {
        boolean shootButter = lastWasButter;

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

        aimLobAtObstacle(owner, gameSession, projectile, Trajectory.LOBBED, false);

        gameSession.getMap().getRow(owner.getY()).addProjectile(projectile);
    }

    @Override
    public int actionVariant() {
        return lastWasButter ? 1 : 0;
    }
}
