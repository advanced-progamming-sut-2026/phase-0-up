package models.entities.zombies.Abilities;

import models.entities.zombies.Zombie;

public class FireImmunityAbility implements ZombieAbility {

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState() == null) return;
        if (!zombie.getState().isImmuneToFire()) {
            zombie.getState().setImmuneToFire(true);
        }
    }
}