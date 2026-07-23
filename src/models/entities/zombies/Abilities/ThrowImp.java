package models.entities.zombies.Abilities;

import factories.ZombieFactory;
import models.entities.zombies.Zombie;

// The Gargantuar hurls its Imp over the defences the moment it drops to half health. The Imp lands in
// the third column from the left of the Gargantuar's own lane, and only ever once per Gargantuar.
public class ThrowImp implements ZombieAbility{
    // The Imp is thrown at half of the Gargantuar's starting health.
    private static final double THROW_AT_HEALTH_FRACTION = 0.5;
    // Third column from the left is index 2; land on its centre so the Imp reads as standing on it.
    private static final double IMP_LANDING_X = 2.5;
    private static final String IMP_ALIAS = "ZombieImp";

    private boolean hasThrownImp;

    public ThrowImp() {
        this.hasThrownImp = false;
    }

    public void setHasThrownImp(boolean hasThrownImp) {
        this.hasThrownImp = hasThrownImp;
    }

    @Override
    public void execute(Zombie zombie) {
        if (hasThrownImp) {
            return;
        }
        // Half of the health it STARTED with -- not an absolute 50 HP, which for a 3600-HP Gargantuar
        // would only fire when it was already a single hit from death.
        int threshold = (int) Math.round(zombie.getHealth().getMaxTotalHp() * THROW_AT_HEALTH_FRACTION);
        if (zombie.getHealth().getTotalHP() > threshold) {
            return;
        }

        int lane = zombie.getMovement().getPositionY();
        Zombie imp = ZombieFactory.createZombie(IMP_ALIAS, IMP_LANDING_X, lane, zombie.getGameSession());
        if (imp == null) {
            return;   // no Imp blueprint registered -- nothing to throw, and never a null on the lawn
        }
        zombie.getGameSession().getMap().getRow(lane).getZombies().add(imp);
        hasThrownImp = true;
        zombie.getGameSession().reportEvent(zombie.getAlias() + " hurls its Imp over your defences onto ("
                + (int) IMP_LANDING_X + ", " + lane + ")!");
    }
}
