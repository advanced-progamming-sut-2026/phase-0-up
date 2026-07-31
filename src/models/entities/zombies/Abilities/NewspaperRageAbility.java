package models.entities.zombies.Abilities;
import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Components.HealthLayer;
import models.entities.zombies.Zombie;
// Newspaper Zombie: slow while the paper is intact, 4x speed and 4x bite once shredded.
public class NewspaperRageAbility implements ZombieAbility {
    private static final double CALM_SPEED_SCALE = 0.5;
    private static final double ENRAGED_SPEED_SCALE = 4.0;
    private static final double ENRAGED_DAMAGE_SCALE = 4.0;
    private boolean calmed = false;
    private boolean enraged = false;
    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getMovement() == null || zombie.getHealth() == null) {
            return;
        }
        if (!calmed) {
            calmed = true;
            zombie.getMovement().setSpeed(zombie.getMovement().getSpeed() * CALM_SPEED_SCALE);
        }
        if (enraged || stillHasNewspaper(zombie)) {
            return;
        }
        enraged = true;
        zombie.getMovement().setSpeed(
                zombie.getMovement().getSpeed() / CALM_SPEED_SCALE * ENRAGED_SPEED_SCALE);
        zombie.scaleEatDamage(ENRAGED_DAMAGE_SCALE);
        zombie.getGameSession().reportEvent("You tore up the " + zombie.getAlias()
                + "'s newspaper -- now he is furious, and coming in fast.");
    }
    private boolean stillHasNewspaper(Zombie zombie) {
        for (HealthLayer layer : zombie.getHealth().getLayers()) {
            if (layer.getType() == ArmorType.NEWSPAPER && layer.getCurrentHp() > 0) {
                return true;
            }
        }
        return false;
    }
}
