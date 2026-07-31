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

    private void placeAll(GameSession gameSession) {
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

    // The plants this level exists to defend cannot be dug up. checkLose watches these exact objects
    // for isDead(), but plucking one only clears the board's reference -- the plant stays alive and
    // un-eatable, so the level would run on with a lose condition that could never fire again.
    @Override
    public boolean isPlantRemovable(int x, int y) {
        for (Plant plant : protectedPlants) {
            if ((int) plant.getX() == x && plant.getY() == y) {
                return false;
            }
        }
        return true;
    }

    // Tells the player up front which plants they are defending, and that losing one ends the level --
    // otherwise the only way to discover the rule is to lose to it.
    @Override
    public void onStart(GameSession gameSession) {
        placeAll(gameSession);
        gameSession.reportEvent(startBanner());
    }

    private String startBanner() {
        if (protectedPlants.isEmpty()) {
            return "Save Our Seeds! Nothing survived the planting, so just hold the lawn.";
        }
        StringBuilder sb = new StringBuilder("Save Our Seeds! Keep these alive at all costs: ");
        for (int i = 0; i < protectedPlants.size(); i++) {
            Plant plant = protectedPlants.get(i);
            if (i > 0) {
                sb.append(i == protectedPlants.size() - 1 ? " and " : ", ");
            }
            sb.append(plant.getName()).append(" at (")
                    .append((int) plant.getX()).append(", ").append(plant.getY()).append(")");
        }
        sb.append(". Lose even one and the level is over -- and no, you can't dig them up.");
        return sb.toString();
    }

    public List<Plant> getProtectedPlants() {
        return protectedPlants;
    }
}
