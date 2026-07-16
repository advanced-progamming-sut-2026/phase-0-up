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
    private boolean started;

    public SaveOurSeedsMode(List<PrePlacedPlant> specs) {
        this.specs = specs != null ? specs : new ArrayList<>();
    }

    @Override
    public void onStart(GameSession gameSession) {
        if (started) {
            return; // placing twice would stack duplicate plants on the same cells
        }
        started = true;
        for (PrePlacedPlant spec : specs) {
            Plant plant = place(gameSession, spec);
            if (plant == null) {
                // Silently skipping would turn this into an ordinary level with no lose condition,
                // so a bad coordinate/plant name in levels.json has to be loud.
                System.err.println("Save Our Seeds: could not pre-place \"" + spec.getPlant()
                        + "\" at (" + spec.getX() + ", " + spec.getY() + ") -- check data/levels.json");
                continue;
            }
            protectedPlants.add(plant);
        }
    }

    private Plant place(GameSession gameSession, PrePlacedPlant spec) {
        if (spec.getPlant() == null || !gameSession.getMap().isValidCoordinate(spec.getX(), spec.getY())) {
            return null;
        }
        int plantLevel = gameSession.getPlayer().getPlantsLevels()
                .getOrDefault(spec.getPlant().toLowerCase().trim(), 1);
        Plant plant = PlantFactory.createPlant(spec.getPlant(), plantLevel, spec.getX(), spec.getY());
        if (plant == null) {
            return null;
        }
        Cell cell = gameSession.getMap().getCell(spec.getX(), spec.getY());
        return cell.addPlant(plant).success() ? plant : null;
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
