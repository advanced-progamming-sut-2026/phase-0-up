package models.game;

import models.entities.zombies.Zombie;

import java.util.List;

public class Wave {
    private int number;
    private boolean isFinal;
    private int WaveCost;
    private List<Zombie> zombies;
    private int totalInitialHp;
    private int delay;

    public void spawn(){};
    public void isDefeated(){};
    public double difficultyFactor(){return 0;}


}
