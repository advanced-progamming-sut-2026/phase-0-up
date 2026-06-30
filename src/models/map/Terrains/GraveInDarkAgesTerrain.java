package models.map.Terrains;

import models.entities.collectibles.PlantFood;
import models.entities.collectibles.Sun;
import models.entities.collectibles.SunType;
import models.game.GameSession;
import models.map.Cell;

import java.util.Random;

public class GraveInDarkAgesTerrain extends GraveTerrain{
    private GravesInDarkAgesTypes type;
    private GameSession gameSession;
    private Cell cell;

    public GraveInDarkAgesTerrain(GravesInDarkAgesTypes type , GameSession gameSession, Cell cell) {
        this.type = type;
        this.symbol = '?';
        this.hp = 700;
        this.gameSession = gameSession;
        this.cell = cell;
    }

    @Override
    public void getShot(int damage){
        hp -= damage;
        if(hp<= 0){
            hp = 0;
            isDead = true;
            dropCollectibles();
        }
    }

    private void dropCollectibles() {
        Random random = new Random();
        double offsetX = random.nextDouble() - 0.5;
        double targetX = cell.getX() + offsetX;
        double offsetY = random.nextDouble() * 0.6;
        double targetY = cell.getY() + offsetY;
        if(type == GravesInDarkAgesTypes.SUNNY){
            Sun sun = new Sun(targetX, cell.getY(), targetY, SunType.NORMAL, 50, true, 100);
            gameSession.addSun(sun);
        } else {
            PlantFood p = new PlantFood(targetX, cell.getY(), targetY, 50, true, 100);
            gameSession.addPlantFood(p);
        }
    }

}
