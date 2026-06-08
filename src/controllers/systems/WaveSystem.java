package controllers.systems;

import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.Wave;

import java.util.List;

//TODO: faster zombie spawn based on difficulty level
//TODO: change waveCost based on player difficulty level
public class WaveSystem {

    public void maybeStartWave(GameSession gameSession){}
    public Wave buildWave(GameSession gameSession){return null;}
    private int calculateBudget(int base, int waveIndex, int difficulty){return 0;}
    private List<Zombie> buyZombies(List<String> allowedZombies, int budget){return null;}
}
