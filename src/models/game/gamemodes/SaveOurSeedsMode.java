package models.game.gamemodes;

import factories.PlantFactory;
import models.entities.plants.Plant;
import models.game.GameSession;
import models.map.Cell;
import models.templates.LevelTemplate.PrePlacedPlant;

import java.util.ArrayList;
import java.util.List;

// Special level: specific plants are pre-planted on the grid at level start. If any of them is
// destroyed (eaten to 0 HP), the player loses immediately. onStart does the placement so the
// session's map exists; the live references are then watched by checkLose.
public class SaveOurSeedsMode extends StandardMode {
    private final List<PrePlacedPlant> specs;
    private final List<Plant> protectedPlants = new ArrayList<>();

    public SaveOurSeedsMode(List<PrePlacedPlant> specs) {
        this.specs = specs != null ? specs : new ArrayList<>();
    }

    @Override
    public void onStart(GameSession gameSession) {
        for (PrePlacedPlant spec : specs) {
            if (!gameSession.getMap().isValidCoordinate(spec.getX(), spec.getY())) {
                continue;
            }
            int plantLevel = gameSession.getPlayer().getPlantsLevels()
                    .getOrDefault(spec.getPlant().toLowerCase().trim(), 1);
            Plant plant = PlantFactory.createPlant(spec.getPlant(), plantLevel, spec.getX(), spec.getY());
            if (plant == null) {
                continue;
            }
            Cell cell = gameSession.getMap().getCell(spec.getX(), spec.getY());
            if (cell.addPlant(plant).success()) {
                protectedPlants.add(plant);
            }
        }
    }

    @Override
    public boolean checkLose(GameSession gameSession) {
        for (Plant plant : protectedPlants) {
            if (plant.isDead()) {
                return true;
            }
        }
        return super.checkLose(gameSession);
    }

    public List<Plant> getProtectedPlants() {
        return protectedPlants;
    }
}
