package models.map;

import models.entities.collectibles.Collectible;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;

import java.util.ArrayList;
import java.util.List;
//rows = y , cols = x
public class GameMap {
    private List<Collectible> activeCollectibles;
    private List<Row> rows;
    
    private static final int ROW_COUNT = 5;
    private static final int COL_COUNT = 9;

    // Big Wave Beach tide state. baseWaterColumn is the leftmost column flooded at level start (the
    // resting waterline); the tide floods up to a few more columns to its left and drains back. -1
    // means this level has no water at all, so the tide never runs.
    private int baseWaterColumn = -1;
    private int tideLevel = 0;
    private boolean tideRising = true;
    private boolean tideAnnounced = false;

    public GameMap() {
        rows = new ArrayList<>();
        activeCollectibles = new ArrayList<>();
        for(int i = 0 ; i < 5; i++){
            Row e = new Row(i);
            rows.add(e);
        }
    }

    public Cell getCell(int x, int y){return rows.get(y).cellAt(x);}
    public Row getRow(int y){return rows.get(y);}

    public List<Row> getRows() {
        return rows;
    }

    public List<Collectible> getActiveCollectibles() {
        return activeCollectibles;
    }

    public boolean isValidCoordinate(int x, int y) {
        return x >= 0 && x < COL_COUNT && y >= 0 && y < ROW_COUNT;
    }

    // Records the resting waterline from the terrain the initializer just flooded: the leftmost column
    // that any row starts flooded in. Called once, after applyTerrain. Stays -1 on a non-beach level.
    public void captureBaseWaterline() {
        int leftmost = COL_COUNT;
        for (Row row : rows) {
            for (int x = 0; x < COL_COUNT; x++) {
                if (row.cellAt(x).isFlooded()) {
                    leftmost = Math.min(leftmost, x);
                    break;
                }
            }
        }
        baseWaterColumn = leftmost < COL_COUNT ? leftmost : -1;
    }

    public boolean hasTide() { return baseWaterColumn >= 0; }
    public int getBaseWaterColumn() { return baseWaterColumn; }
    public int getTideLevel() { return tideLevel; }
    public void setTideLevel(int level) { this.tideLevel = level; }
    public boolean isTideRising() { return tideRising; }
    public void setTideRising(boolean rising) { this.tideRising = rising; }
    public boolean isTideAnnounced() { return tideAnnounced; }
    public void setTideAnnounced(boolean announced) { this.tideAnnounced = announced; }

    // The leftmost column the tide can ever flood. Capped by both TIDE_MAX_RISE (how far the water
    // pushes) and TIDE_SAFE_COLUMNS (a hard floor so the first columns are never flooded, whatever the
    // level's waterline). Columns 0 .. floor-1 are always safe to plant on. -1 on a non-beach level.
    public int getTideFloodFloor() {
        if (!hasTide()) {
            return -1;
        }
        return Math.max(utils.Constants.TIDE_SAFE_COLUMNS, baseWaterColumn - utils.Constants.TIDE_MAX_RISE);
    }

    // How many leftmost columns (0 .. n-1) the tide is guaranteed never to reach.
    public int getSafeColumnCount() {
        return hasTide() ? getTideFloodFloor() : COL_COUNT;
    }
    public List<Zombie> killAllZombies() {
        List<Zombie> killedZombies = new ArrayList<>();
        for (Row row : rows) {
            List<Zombie> zombiesInRow = new ArrayList<>(row.getZombies());
            for (Zombie zombie : zombiesInRow) {
                if (!zombie.getHealth().isDead()) {
                    zombie.getHealth().applyDamage(zombie.getHealth().getTotalHP(), Element.NEUTRAL, null);
                }
                killedZombies.add(zombie);
            }
            row.getZombies().removeAll(zombiesInRow);
        }
        return killedZombies;
    }
}
