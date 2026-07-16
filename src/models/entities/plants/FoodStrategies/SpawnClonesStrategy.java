package models.entities.plants.FoodStrategies;

import factories.PlantFactory;
import models.entities.plants.Plant;
import models.game.GameSession;
import models.map.Cell;
import models.map.Row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Plant food: drops up to N fresh copies of the source plant onto empty tiles (Potato Mine's extra
// mines, Lily Pad's spread). Each clone is built by the factory at the same level, so it comes with
// its own abilities and upgrades; Cell.addPlant enforces the land/water rules.
public class SpawnClonesStrategy implements PlantFoodStrategy {
    private final int count;

    public SpawnClonesStrategy(int count) {
        this.count = count;
    }

    @Override
    public void executeEffect(Plant sourcePlant, GameSession gameSession) {
        List<Cell> candidates = emptyCells(gameSession);
        Collections.shuffle(candidates);

        int placed = 0;
        for (Cell cell : candidates) {
            if (placed >= count) {
                break;
            }
            int col = (int) cell.getX();
            Plant clone = PlantFactory.createPlant(sourcePlant.getName(), sourcePlant.getLevel(), col, cell.getY());
            if (clone != null && cell.addPlant(clone).success()) {
                placed++;
            }
        }
    }

    private List<Cell> emptyCells(GameSession gameSession) {
        List<Cell> cells = new ArrayList<>();
        for (Row row : gameSession.getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                if (!cell.hasPlant() && cell.isPlantable()) {
                    cells.add(cell);
                }
            }
        }
        return cells;
    }
}
