package models.entities.zombies.Abilities;

import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Components.HealthLayer;
import models.entities.zombies.Zombie;

// Newspaper Zombie: an old man shuffling along behind his paper. While the newspaper is intact he reads
// as he walks and is SLOWER than an ordinary zombie; the moment the paper is shredded he loses his
// temper and both his walking speed and his bite get several times stronger
// (documents/project.md, Newspaper Zombie).
//
// The blueprint's Speed is the calm-walk reference, and the scales mirror the ones the zombie sheet
// ships with (EnragedSpeedScale 4, EnragedDamageScale 4 in data/zombie-data/zombies.json). Keeping them
// here rather than on ZombieTemplate follows the same convention as the other behaviour-specific
// numbers -- LaserBeamAbility's charge and damage figures are held the same way.
public class NewspaperRageAbility implements ZombieAbility {
    // Reading while you walk is slow work: the calm shuffle is half the blueprint's speed, which puts
    // this zombie below every ordinary walker until the paper goes.
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

        // Slowed down on the first tick rather than at construction: the ability is built before the
        // Zombie that owns it, so there is no movement component to slow until it starts ticking.
        if (!calmed) {
            calmed = true;
            zombie.getMovement().setSpeed(zombie.getMovement().getSpeed() * CALM_SPEED_SCALE);
        }

        if (enraged || stillHasNewspaper(zombie)) {
            return;
        }

        enraged = true;
        // Back to the blueprint speed first (undoing the calm halving), then up by the enraged scale,
        // so the multiplier reads against the sheet value and not against the shuffle.
        zombie.getMovement().setSpeed(
                zombie.getMovement().getSpeed() / CALM_SPEED_SCALE * ENRAGED_SPEED_SCALE);
        zombie.scaleEatDamage(ENRAGED_DAMAGE_SCALE);
        zombie.getGameSession().reportEvent("You tore up the " + zombie.getAlias()
                + "'s newspaper -- now he is furious, and coming in fast.");
    }

    // The paper is a health layer like any other armor; it is popped off the stack the moment it is
    // destroyed, so its absence from the stack is exactly "the newspaper is gone".
    private boolean stillHasNewspaper(Zombie zombie) {
        for (HealthLayer layer : zombie.getHealth().getLayers()) {
            if (layer.getType() == ArmorType.NEWSPAPER && layer.getCurrentHp() > 0) {
                return true;
            }
        }
        return false;
    }
}
