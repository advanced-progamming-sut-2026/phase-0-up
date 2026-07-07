package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;

public class KillPlantsAbility implements ZombieAbility {

    @Override
    public void execute(Zombie zombie) {
        if (zombie.getState().isUnableToMove()) return;

        if (!zombie.getState().isTorchLit()) return;

        Plant targetPlant = zombie.getTargetPlantInFront();

        if (targetPlant != null) {
            targetPlant.getHealth().takeDamage(targetPlant.getHealth().getMaxHp());
        }
    }
}