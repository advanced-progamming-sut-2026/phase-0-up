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

    public Pot(int x, int y) {
        this.x = x;
        this.y = y;
        this.state = PotState.EMPTY;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}
