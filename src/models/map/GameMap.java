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
    
    private static final int rowCount = 5;
    private static final int colCount = 9;

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
        return x >= 0 && x < colCount && y >= 0 && y < rowCount;
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
