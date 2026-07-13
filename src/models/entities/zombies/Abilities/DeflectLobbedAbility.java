package models.entities.zombies.Abilities;

import models.entities.plants.classification.Lobbers;
import models.entities.projectiles.Projectile;
import models.entities.zombies.Zombie;

public class DeflectLobbedAbility implements ZombieAbility {

    private boolean isParasolIntact = true;

    @Override
    public void execute(Zombie zombie) {
    }


    public boolean canDeflect(Projectile projectile) {
        return isParasolIntact && (projectile.getShooter() instanceof Lobbers);
    }

    public boolean isParasolIntact() { return isParasolIntact; }

    public void destroyParasol() {
        this.isParasolIntact = false;
    }

    public void handleProjectileHit(Zombie zombie, Projectile projectile) {
        for (ZombieAbility ability : zombie.getAbilities()) {
            if (ability instanceof DeflectLobbedAbility) {
                DeflectLobbedAbility parasol = (DeflectLobbedAbility) ability;
                if (parasol.canDeflect(projectile)) {
                    projectile.destroy();
                    return;
                }
            }
        }
        zombie.getHealth().applyDamage(projectile.getDamage() , projectile.getDamageType(), projectile.getShooter());
        projectile.destroy();
    }
}