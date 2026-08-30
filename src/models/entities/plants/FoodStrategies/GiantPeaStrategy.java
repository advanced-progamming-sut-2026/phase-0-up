package models.entities.plants.FoodStrategies;

import models.entities.plants.Plant;
import models.entities.plants.abilities.PlantAbility;
import models.entities.plants.abilities.ShootProjectileAbility;
import models.entities.plants.components.StackableComponent;
import models.game.GameSession;

// GIANT_PEA_BURST plant-food: fires N giant peas (20x damage) from the plant's shooter (Repeater, Pea Pod, ...).
public class GiantPeaStrategy implements PlantFoodStrategy {
    private static final int GIANT_DAMAGE_MULTIPLIER = 20;
    private int count;

    public GiantPeaStrategy(int count) {
        this.count = count;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        int shots = shotsFor(sourcePlant);
        for (PlantAbility ability : sourcePlant.getAbilities()) {
            if (ability instanceof ShootProjectileAbility) {
                ((ShootProjectileAbility) ability).queueGiantShots(shots, GIANT_DAMAGE_MULTIPLIER);
            }
        }
    }

    // One giant pea per pea on the pod. A Pea Pod is a single plant carrying up to five heads and every
    // head throws one, so a fixed count either short-changes a full pod or over-pays a lone head. The
    // configured value stays the answer for a shooter that does not stack (Repeater).
    private int shotsFor(Plant plant) {
        StackableComponent heads = plant.getStackableComponent();
        return heads == null ? count : Math.max(1, heads.getCurrentStacks());
    }
}
