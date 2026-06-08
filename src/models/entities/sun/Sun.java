package models.entities.sun;

import models.entities.Entity;

public class Sun extends Entity {
    private int amount;
    private long expireTime;
    private boolean isFalling;
    private double targetY;


    @Override
    public void update() {}
}
