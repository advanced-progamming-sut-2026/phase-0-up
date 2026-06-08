package controllers.systems;

import models.game.GameSession;

public class SunSystem {

    public double dropRate(long elapsedTime){return 0;}
    public void SpawnSkySun(GameSession gameSession){}
    public void SpawnPlantSun(GameSession gameSession, double x, int y, int amount){}
    public void collectSun(GameSession gameSession, double x, int y){}

}
