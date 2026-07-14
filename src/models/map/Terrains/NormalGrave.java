package models.map.Terrains;

import models.game.GameSession;
import models.map.Cell;

public class NormalGrave extends GraveTerrain{
    private GameSession gameSession;
    private Cell cell;

    public NormalGrave(GameSession gameSession, Cell cell) {
        this.hp = 700;
        this.isDead = false;
        this.plantable = false;
        this.gameSession = gameSession;
        this.cell = cell;
    }


    @Override
    public void getShot(int damage) {
        hp -= damage;
        if(hp<= 0){
            hp = 0;
            isDead = true;
        }
    }
}
