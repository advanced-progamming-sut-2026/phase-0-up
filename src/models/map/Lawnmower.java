package models.map;

import models.entities.zombies.Zombie;
import models.game.GameSession;

import java.util.List;

public class Lawnmower {
    private boolean used;
    private int row;
    private final double lawnmowerSpeed = 0.6;
    private double positionX;
    private boolean isActiveNow;

    public Lawnmower(int row) {
        this.used = false;
        this.row = row;
        this.isActiveNow = false;
        this.positionX = 0;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public void update(GameSession gameSession){
        int index = this.getRow();
        List<Zombie> zombies = gameSession.getMap().getRows().get(index).getZombies();
        for(Zombie z : zombies){
            if(z.getMovement().getPositionX() >= this.positionX &&
                    z.getMovement().getPositionX() <= this.positionX + lawnmowerSpeed){
                z.getHealth().applyDamage(z.getHealth().getTotalHP() , null);
            }
        }
        this.setPositionX(this.positionX += lawnmowerSpeed);
        if(this.positionX > 9) {
            this.used = true;
            this.isActiveNow = false;
        }
    }

    public void setPositionX(double positionX) {
        this.positionX = positionX;
    }
}
