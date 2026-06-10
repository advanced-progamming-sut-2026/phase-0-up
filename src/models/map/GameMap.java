package models.map;

import java.util.List;

public class GameMap {
    private List<Row> rows;
    static int rowCount = 5;
    static int colCount = 9;
    public Cell getCell(int x, int y){return null;}
    public Row getRow(int x){return null;}
    List<Collectible> activeCollectibles;


}
