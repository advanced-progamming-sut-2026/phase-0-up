package views.renderers;

import models.entities.interactables.GargantuarVase;
import models.entities.interactables.PlantVase;
import models.entities.interactables.Vase;
import models.entities.plants.Plant;
import models.entities.plants.bowling.BowlingKind;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.gamemodes.BeghouledMode;
import models.game.gamemodes.VaseBreakerMode;
import models.game.gamemodes.WallnutBowlingMode;
import models.map.Cell;
import models.map.GameMap;
import models.map.Lawnmower;
import models.map.Row;
import models.map.Terrains.Terrain;
import views.OutputHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MapRenderer {
    public void renderAllTheMap(GameSession activeSession){
        GameMap map = activeSession.getMap();
        if (map.hasTide()) {
            int floor = map.getTideFloodFloor();
            OutputHandler.showMessage("Tide: columns " + floor + "+ may flood; columns 0-" + (floor - 1)
                    + " are always safe.");
        }
        for (Row row : map.getRows()) {
            OutputHandler.showMessage(buildRowLine(activeSession, row));
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
    private String buildRowLine(GameSession session, Row row) {
        StringBuilder line = new StringBuilder();
        // Some modes have no lawn mowers (Vasebreaker nulls them out), so guard against a null mower --
        // reading it blindly is what crashed "show map" in Vasebreaker.
        Lawnmower mower = row.getLawnmower();
        String mowerState = mower == null ? "no mower" : (mower.isUsed() ? "mower used" : "mower ready");
        line.append("Row ").append(row.getIndex()).append(" [").append(mowerState).append("]: ");
        for (Cell cell : row.getCells()) {
            line.append(cellSymbol(session, row, cell)).append(' ');
        }
        return line.toString().trim();
    }

    private String cellSymbol(GameSession session, Row row, Cell cell) {
        int column = (int) cell.getX();
        boolean zombieHere = false;
        for (Zombie zombie : row.getZombies()) {
            if (!zombie.getHealth().isDead() && (int) zombie.getMovement().getPositionX() == column) {
                zombieHere = true;
                break;
            }
        }
        // Beghouled is a match-3: the player has to tell the plant types apart to plan a swap, so its
        // board renders a two-letter code per plant instead of one anonymous [P].
        if (session != null && session.getMode() instanceof BeghouledMode beghouled) {
            return beghouledSymbol(beghouled, column, row.getIndex(), zombieHere);
        }
        if (zombieHere) {
            return "[Z]";
        }
        if (cell.hasPlant() || cell.hasProtector()) {
            return "[P]";
        }
        // Vasebreaker's unique board state. The three vase types are visually distinct: [G] the
        // guaranteed Gargantuar vase, [*] the guaranteed plant vase, [?] an ordinary unknown vase.
        // The plant vase deliberately does NOT use [P] -- that already means a plant standing on the
        // cell, and reusing it would make the two indistinguishable on a Vasebreaker board.
        Vase vase = vaseAt(session, column, row.getIndex());
        if (vase != null && !vase.isBroken()) {
            if (vase instanceof GargantuarVase) {
                return "[G]";
            }
            if (vase instanceof PlantVase) {
                return "[*]";
            }
            return "[?]";
        }
        // A seed packet lying on the grid, still collectable until its timeout destroys it.
        if (hasDroppedSeed(session, column, row.getIndex())) {
            return "[S]";
        }
        for (Terrain terrain : cell.getTerrain()) {
            if (!terrain.isDestroyed()) {
                return "[" + terrain.getSymbol() + "]";
            }
        }
        return cell.isPlantable() ? "[.]" : "[x]";
    }

    // The vase on cell (x, y), or null when this isn't a Vasebreaker level or the cell has no vase.
    private Vase vaseAt(GameSession session, int x, int y) {
        if (session != null && session.getMode() instanceof VaseBreakerMode vaseBreaker) {
            return vaseBreaker.getVaseAt(x, y);
        }
        return null;
    }

    // One Beghouled tile: a zombie standing on it, a crater a zombie hollowed out (nothing may ever be
    // placed there again), an empty tile, or the two-letter code of the plant sitting there.
    private String beghouledSymbol(BeghouledMode mode, int column, int rowIndex, boolean zombieHere) {
        if (zombieHere) {
            return "[Z!]";
        }
        if (mode.board() == null) {
            return "[..]";   // board not built yet (mode never started)
        }
        if (mode.isCrater(rowIndex, column)) {
            return "[##]";
        }
        String plantType = mode.typeAt(rowIndex, column);
        return plantType == null ? "[..]" : "[" + plantCode(plantType) + "]";
    }

    // Two-character code for a Beghouled plant: the initials of a multi-word name (Mega Gatling Pea ->
    // MG), otherwise its first two letters (Peashooter -> Pe). Chosen so no two plant types that can
    // share a board collapse onto the same code.
    private String plantCode(String name) {
        String trimmed = name.trim();
        String[] words = trimmed.split("\\s+");
        if (words.length >= 2 && !words[0].isEmpty() && !words[1].isEmpty()) {
            return "" + Character.toUpperCase(words[0].charAt(0))
                    + Character.toUpperCase(words[1].charAt(0));
        }
        if (trimmed.length() >= 2) {
            return "" + Character.toUpperCase(trimmed.charAt(0))
                    + Character.toLowerCase(trimmed.charAt(1));
        }
        return (trimmed + "?").substring(0, 2);
    }

    // Whether an uncollected seed packet is currently lying on cell (x, y) (Vasebreaker only).
    private boolean hasDroppedSeed(GameSession session, int x, int y) {
        return session != null && session.getMode() instanceof VaseBreakerMode vaseBreaker
                && vaseBreaker.hasDroppedSeed(x, y);
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
        renderModeStatus(activeSession);
        renderAllTheMap(activeSession);
    }

    // Mode-specific status the player needs in front of them every time they look at the lawn: the
    // bowling conveyor (what you can actually throw) and the Beghouled match tally.
    private void renderModeStatus(GameSession session) {
        if (session == null) {
            return;
        }
        if (session.getMode() instanceof WallnutBowlingMode bowling) {
            StringBuilder nuts = new StringBuilder();
            for (Map.Entry<BowlingKind, Integer> entry : bowling.conveyorCounts().entrySet()) {
                if (nuts.length() > 0) {
                    nuts.append(", ");
                }
                nuts.append(entry.getKey().name().toLowerCase()).append(" x").append(entry.getValue());
            }
            OutputHandler.showMessage("Wave " + bowling.getWavesReleased() + "/" + bowling.getTotalWaves()
                    + "  |  Nuts ready (" + bowling.conveyorSize() + "/" + bowling.conveyorCapacity()
                    + "): " + nuts);
        } else if (session.getMode() instanceof BeghouledMode beghouled) {
            OutputHandler.showMessage("Matches: " + beghouled.getMatchesMade() + "/"
                    + beghouled.getMatchTarget() + "  |  spend sun with: upgrade -t <plant>");
        }
    }
}
