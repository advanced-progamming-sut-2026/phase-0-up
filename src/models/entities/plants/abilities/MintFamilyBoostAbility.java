package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.game.GameSession;
import models.map.Cell;
import models.map.Row;

// Triggers plant-food on every planted plant of the mint's own family (category), then is consumed.
public class MintFamilyBoostAbility extends PlantAbility {
    private boolean hasExecuted;

    public MintFamilyBoostAbility() {
        super(0, null);
        this.hasExecuted = false;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        return !hasExecuted;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        hasExecuted = true;

        String family = owner.getCategory();
        if (family != null) {
            for (Row row : gameSession.getMap().getRows()) {
                for (Cell cell : row.getCells()) {
                    Plant plant = cell.getCurrentPlant();
                    if (plant != null && plant != owner && !plant.isDead()
                            && family.equals(plant.getCategory())) {
                        plant.triggerPlantFood(gameSession);
                    }
                }
            }
        }

        if (owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }
}
