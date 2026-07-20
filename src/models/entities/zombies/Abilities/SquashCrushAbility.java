package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;

// Zombotany Squash zombie: charges in fast (its speed is set high on the template) and the moment it
// reaches a plant it crushes that plant -- and is itself destroyed in the act.
public class SquashCrushAbility implements ZombieAbility {
    private boolean crushed;

    @Override
    public void execute(Zombie zombie) {
        if (crushed || zombie == null || zombie.getHealth().isDead() || !zombie.isOnBoard()) {
            return;
        }
        Plant target = zombie.getTargetPlantInFront();
        if (target != null && !target.isDead() && target.getHealth() != null) {
            crushed = true;
            target.getHealth().takeDamage(Integer.MAX_VALUE);
            zombie.getHealth().applyDamage(zombie.getHealth().getTotalHP(), Element.NEUTRAL, null);
        }
    }
}
