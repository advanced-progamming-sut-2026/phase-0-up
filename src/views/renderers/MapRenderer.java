package views.renderers;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Cell;
import models.map.GameMap;
import models.map.Row;
import models.map.Terrains.Terrain;
import views.OutputHandler;

import java.util.ArrayList;
import java.util.List;

public class MapRenderer {
    public void renderAllTheMap(GameSession activeSession){
        GameMap map = activeSession.getMap();
        for (Row row : map.getRows()) {
            OutputHandler.showMessage(buildRowLine(row));
        }
        renderAllZombies(collectAllZombies(map));
        renderAllPlants(collectAllPlantCells(map));
    }

    private List<Zombie> collectAllZombies(GameMap map) {
        List<Zombie> zombies = new ArrayList<>();
        for (Row row : map.getRows()) {
            zombies.addAll(row.getZombies());
        }
        return zombies;
    }

    private List<Cell> collectAllPlantCells(GameMap map) {
        List<Cell> cells = new ArrayList<>();
        for (Row row : map.getRows()) {
            for (Cell cell : row.getCells()) {
                if (cell.hasPlant()) {
                    cells.add(cell);
                }
            }
        }
        return cells;
    }
    private String buildRowLine(Row row) {
        StringBuilder line = new StringBuilder();
        line.append("Row ").append(row.getIndex())
                .append(" [").append(row.getLawnmower().isUsed() ? "mower used" : "mower ready").append("]: ");
        for (Cell cell : row.getCells()) {
            line.append(cellSymbol(row, cell)).append(' ');
        }
        return line.toString().trim();
    }

    private String cellSymbol(Row row, Cell cell) {
        int column = (int) cell.getX();
        for (Zombie zombie : row.getZombies()) {
            if (!zombie.getHealth().isDead() && (int) zombie.getMovement().getPositionX() == column) {
                return "[Z]";
            }
        }
        if (cell.hasPlant() || cell.hasProtector()) {
            return "[P]";
        }
        for (Terrain terrain : cell.getTerrain()) {
            if (!terrain.isDestroyed()) {
                return "[" + terrain.getSymbol() + "]";
            }
        }
        return cell.isPlantable() ? "[.]" : "[x]";
    }


    public void renderAllZombies(List<Zombie> activeZombies){
        if (activeZombies.isEmpty()) {
            OutputHandler.showMessage("No zombies on the field.");
            return;
        }
        OutputHandler.showMessage("Zombies:");
        for (Zombie zombie : activeZombies) {
            OutputHandler.showMessage("  " + zombie.getAlias() + " at (" + (int) zombie.getMovement().getPositionX()
                    + ", " + zombie.getMovement().getPositionY() + ") - health: " + zombie.getHealth().getTotalHP());
        }
    }
    public void renderAllPlants(List<Cell> cells){
        if (cells.isEmpty()) {
            OutputHandler.showMessage("No plants on the field.");
            return;
        }
        OutputHandler.showMessage("Plants:");
        for (Cell cell : cells) {
            Plant plant = cell.getCurrentPlant();
            OutputHandler.showMessage("  " + plant.getName() + " at (" + (int) cell.getX() + ", " + cell.getY()
                    + ") - health: " + plant.getHealth().getCurrentHp());
        }
    }

    public void renderGameSession(GameSession activeSession){
        OutputHandler.showMessage("Wave: " + activeSession.getCurrentWave());
        OutputHandler.showMessage("Sun: " + activeSession.getSunAmount());
        OutputHandler.showMessage("Plant food: " + activeSession.getPlantFoodCount());
        renderAllTheMap(activeSession);
    }
}
