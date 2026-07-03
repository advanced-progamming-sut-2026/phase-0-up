package models.map;

import models.entities.collectibles.Collectible;

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
}
