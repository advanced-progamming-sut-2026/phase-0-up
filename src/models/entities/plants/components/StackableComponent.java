package models.entities.plants.components;

import models.entities.plants.Plant;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.abilities.ShootProjectileAbility;

public class StackableComponent {
    private Plant owner;
    private int maxStacks;
    private int currentStacks;

    private int healthBonusPerStack;

    public StackableComponent(Plant owner, int maxStacks, int healthBonusPerStack) {
        this.owner = owner;
        this.maxStacks = maxStacks;
        this.currentStacks = 1;
        this.healthBonusPerStack = healthBonusPerStack;
    }

    public boolean addStack() {
        if (currentStacks >= maxStacks) {
            return false;
        }

        currentStacks++;

        if (owner.getHealth() != null) {
            owner.getHealth().setMaxHp(owner.getHealth().getMaxHp() + healthBonusPerStack);
            owner.getHealth().heal(healthBonusPerStack);
        }

        if (owner.getAbilities() != null) {
            for (PlantAbility ability : owner.getAbilities()) {
                if (ability instanceof ShootProjectileAbility) {
                    ((ShootProjectileAbility) ability).increaseShotCount(1);
                }
            }
        }

        return true;
    }

    public int getCurrentStacks() {
        return currentStacks;
    }
}
