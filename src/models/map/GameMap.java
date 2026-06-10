package models.map;

import models.entities.collectibles.Collectible;

import java.util.List;

public class GameMap {
    private List<Row> rows;
    private static final int rowCount = 5;
    private static final int colCount = 9;
    public Cell getCell(int x, int y){return null;}
    public Row getRow(int x){return null;}
    private List<Collectible> activeCollectibles;


}
