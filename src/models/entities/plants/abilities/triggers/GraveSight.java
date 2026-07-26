package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.game.GameSession;
import models.map.Cell;
import models.map.Row;
import models.map.Terrains.GraveTerrain;
import models.map.Terrains.Terrain;
import utils.Constants;

// "Is there a grave for this plant to shoot at?" -- the grave half of a shooter's line of sight.
//
// A grave is a solid obstacle that blocks shots and blocks planting, so a straight-firing plant has to
// be able to chip it down even when no zombie is on the lawn; otherwise a lane walled off by graves can
// never be reopened. Lobbed shooters (the -pult family) arc over terrain and cannot damage a grave at
// all, so they never consult this -- see TriggerResolver, which is the only thing that turns it on.
//
// Sight here is deliberately geometric, not a full trace: a grave in range counts even if another grave
// stands between it and the plant. The nearer one absorbs the shot anyway, so the plant fires either
// way and the shot lands on whichever grave it reaches first.
public final class GraveSight {
    private GraveSight() { }

    // A standing grave ahead of the plant in its own lane, within `range` tiles (range <= 0 means the
    // rest of the lane, matching how the standard forward trigger sees zombies).
    public static boolean graveAhead(Plant owner, GameSession gameSession, double range) {
        return graveInRow(owner, gameSession, owner.getY(), range, true);
    }

    // A standing grave behind the plant in its own lane (Split Pea's rear barrel).
    public static boolean graveBehind(Plant owner, GameSession gameSession) {
        return graveInRow(owner, gameSession, owner.getY(), 0.0, false);
    }

    // A standing grave ahead of the plant in any of the lanes it covers (Threepeater).
    public static boolean graveInLanes(Plant owner, GameSession gameSession, int[] rowOffsets) {
        if (rowOffsets == null) {
            return false;
        }
        for (int offset : rowOffsets) {
            if (graveInRow(owner, gameSession, owner.getY() + offset, 0.0, true)) {
                return true;
            }
        }
        return false;
    }

    // The single scan every case above reduces to: walk one lane's cells and look for a grave that is
    // still standing on the correct side of the plant, and close enough to be worth firing at.
    private static boolean graveInRow(Plant owner, GameSession gameSession, int rowIndex,
                                      double range, boolean forward) {
        if (owner == null || gameSession == null || gameSession.getMap() == null) {
            return false;
        }
        if (rowIndex < 0 || rowIndex >= Constants.BOARD_ROWS) {
            return false;
        }
        Row row = gameSession.getMap().getRow(rowIndex);
        if (row == null || row.getCells() == null) {
            return false;
        }
        double ownerX = owner.getX();

        for (Cell cell : row.getCells()) {
            if (cell == null || !hasStandingGrave(cell)) {
                continue;
            }
            double cellX = cell.getX();
            if (forward) {
                if (cellX <= ownerX) {
                    continue;
                }
                // A ranged shooter (Fume-shroom and friends) only opens fire once the grave is inside
                // the distance its shots actually travel; an unranged one takes the whole lane.
                if (range > 0.0 && cellX > ownerX + range) {
                    continue;
                }
            } else if (cellX >= ownerX) {
                continue;
            }
            return true;
        }
        return false;
    }

    // A grave that is present and not yet broken. A destroyed grave may still be sitting in the cell's
    // terrain list until something sweeps it, so "not destroyed" is the test, never mere presence.
    private static boolean hasStandingGrave(Cell cell) {
        if (cell.getTerrain() == null) {
            return false;
        }
        for (Terrain terrain : cell.getTerrain()) {
            if (terrain instanceof GraveTerrain && !terrain.isDestroyed()) {
                return true;
            }
        }
        return false;
    }
}
