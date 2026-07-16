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
            System.out.println(zombie.getAlias() + " shrugged off the freeze!");
        }

        if (zombie.getState().isChilled()) {
            zombie.getState().setChilledTimer(0);
            System.out.println(zombie.getAlias() + " ignored the chill effect!");
        }
    }

    public boolean isImmuneToIce() {
        return true;
    }
}