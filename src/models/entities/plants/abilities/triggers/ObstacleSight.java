package models.entities.plants.abilities.triggers;

import models.entities.plants.Plant;
import models.game.GameSession;
import models.map.Cell;
import models.map.Row;
import models.map.Terrains.Terrain;
import utils.Constants;

// "Is there something in the way for this plant to shoot at?" -- the terrain half of a shooter's line
// of sight.
//
// A grave and a Frostbite ice block are both solid: they block shots and block planting, so a shooter
// has to be able to chip one down even when no zombie is on the lawn; otherwise a lane walled off by
// them can never be reopened. See TriggerResolver, which is the only thing that turns this on.
//
// This was GraveSight and saw only headstones, which left the ice blocks in a half-built state: shots
// already damaged them (Terrain.blocksProjectiles has been true on FrozenTerrain since Phase 1) but
// nothing would ever aim one, so a lane walled off by ice could only be opened by fire or by waiting
// out the melt. The predicate is now the blocking flag itself rather than a list of classes.
//
// Lobbed shooters (the -pult family) consult this too, and did NOT used to. Their shots arc over
// terrain by design -- which is right while there is a zombie to arc at, and useless in a lane that
// holds nothing but obstacles. A -pult now opens fire on one only when its lane is empty of
// zombies, and the shot it fires is marked to come down on it rather than sail past it
// (Projectile.setTerrainTarget). A zombie always wins: the triggers test zombies first and only fall
// through to here, and ShootProjectileAbility only marks the shot when nothing targetable is ahead.
//
// Sight here is geometric, not a full trace: an obstacle in range counts even if another one
// stands between it and the plant. The nearer one absorbs the shot anyway, so the plant fires either
// way and the shot lands on whichever it reaches first.
public final class ObstacleSight {
    private ObstacleSight() { }

    // A standing obstacle ahead of the plant in its own lane, within `range` tiles (range <= 0 means the
    // rest of the lane, matching how the standard forward trigger sees zombies).
    public static boolean obstacleAhead(Plant owner, GameSession gameSession, double range) {
        return obstacleInRow(owner, gameSession, owner.getY(), range, true);
    }

    // A standing obstacle behind the plant in its own lane (Split Pea's rear barrel).
    public static boolean obstacleBehind(Plant owner, GameSession gameSession) {
        return obstacleInRow(owner, gameSession, owner.getY(), 0.0, false);
    }

    // A standing obstacle ahead of the plant in any of the lanes it covers (Threepeater).
    public static boolean obstacleInLanes(Plant owner, GameSession gameSession, int[] rowOffsets) {
        if (rowOffsets == null) {
            return false;
        }
        for (int offset : rowOffsets) {
            if (obstacleInRow(owner, gameSession, owner.getY() + offset, 0.0, true)) {
                return true;
            }
        }
        return false;
    }

    // Where the nearest standing obstacle ahead of the plant is, as a continuous x, or -1 when there is
    // none. The centre of the cell rather than its index: that is the point a shot has to reach for
    // Projectile.handleTerrainCollisions to count the hit, so aiming anywhere else would either land
    // the lob short of the obstacle or carry it past.
    public static double nearestObstacleAheadX(Plant owner, GameSession gameSession) {
        if (owner == null || gameSession == null || gameSession.getMap() == null) {
            return -1.0;
        }
        int rowIndex = owner.getY();
        if (rowIndex < 0 || rowIndex >= Constants.BOARD_ROWS) {
            return -1.0;
        }
        Row row = gameSession.getMap().getRow(rowIndex);
        if (row == null || row.getCells() == null) {
            return -1.0;
        }
        double nearest = -1.0;
        for (Cell cell : row.getCells()) {
            if (cell == null || !hasStandingObstacle(cell) || cell.getX() <= owner.getX()) {
                continue;
            }
            if (nearest < 0.0 || cell.getX() < nearest) {
                nearest = cell.getX();
            }
        }
        return nearest;
    }

    // The single scan every case above reduces to: walk one lane's cells and look for an obstacle that is
    // still standing on the correct side of the plant, and close enough to be worth firing at.
    private static boolean obstacleInRow(Plant owner, GameSession gameSession, int rowIndex,
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
            if (cell == null || !hasStandingObstacle(cell)) {
                continue;
            }
            double cellX = cell.getX();
            if (forward) {
                if (cellX <= ownerX) {
                    continue;
                }
                // A ranged shooter (Fume-shroom and friends) only opens fire once the obstacle is inside
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

    // Something in the way that is still standing.
    //
    // Asked as "does this block a shot", not as a list of classes. Terrain.doesBlockProjectiles is the
    // exact same flag Projectile.handleTerrainCollisions consults to decide whether a shot damages a
    // piece of terrain, so a shooter now opens fire on precisely the things its shots can actually hurt
    // -- today a grave and a Frostbite ice block, and tomorrow whatever else is given the flag, with
    // nothing here to remember to update.
    //
    // It was `instanceof GraveTerrain`, which is why an ice block walling off a lane could never be shot
    // open: the shots would have damaged it perfectly well, and no plant would ever fire one.
    //
    // "Not destroyed" is the test, never mere presence: a broken grave or a melted block may still be
    // sitting in the cell's terrain list until something sweeps it.
    private static boolean hasStandingObstacle(Cell cell) {
        if (cell.getTerrain() == null) {
            return false;
        }
        for (Terrain terrain : cell.getTerrain()) {
            if (terrain.doesBlockProjectiles() && !terrain.isDestroyed()) {
                return true;
            }
        }
        return false;
    }
}
