package models.entities.zombies.Abilities;

import models.entities.projectiles.Projectile;
import models.entities.projectiles.Trajectory;
import models.entities.zombies.Zombie;

// Parasol Zombie: its umbrella repels every lobber's shot; Projectile.onHit calls deflects().
public class DeflectLobbedAbility implements ZombieAbility {

    private boolean isParasolIntact = true;

    @Override
    public void execute(Zombie zombie) {
    }


    public boolean canDeflect(Projectile projectile) {
        return isParasolIntact && (projectile.getTrajectory() == Trajectory.LOBBED);
    }

    public boolean isParasolIntact() { return isParasolIntact; }

    public void destroyParasol() {
        this.isParasolIntact = false;
    }

    public static boolean deflects(Zombie zombie, Projectile projectile) {
        for (ZombieAbility ability : zombie.getAbilities()) {
            if (ability instanceof DeflectLobbedAbility parasol && parasol.canDeflect(projectile)) {
                return true;
            }
        }
        return false;
    }
}