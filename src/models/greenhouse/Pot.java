package models.greenhouse;

import models.entities.plants.Plant;

public class Pot{
    private int x;
    private int y;
    private PotState state;
    private String growing;
    private long readyAtTick;
    private int speed;
    private Plant onPot;


    public void unlock(){};
}
