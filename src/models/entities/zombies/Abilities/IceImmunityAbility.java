package models.entities.zombies.Abilities;

import models.entities.zombies.Zombie;

public class IceImmunityAbility implements ZombieAbility {

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState() == null) {
            return;
        }

        if (zombie.getState().isFrozen()) {
            zombie.getState().setFrozen(false);
            zombie.getState().setFrozenTimer(0);
            zombie.getGameSession().reportEvent(zombie.getAlias() + " shrugs off the freeze at ("
                    + (int) zombie.getX() + ", " + zombie.getY() + ").");
        }

        if (zombie.getState().isChilled()) {
            zombie.getState().setChilledTimer(0);
            zombie.getGameSession().reportEvent(zombie.getAlias() + " ignores the chill at ("
                    + (int) zombie.getX() + ", " + zombie.getY() + ").");
        }
    }

    public boolean isImmuneToIce() {
        return true;
    }
}