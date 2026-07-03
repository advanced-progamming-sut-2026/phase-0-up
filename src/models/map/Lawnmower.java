package models.map;

import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.ArrayList;
import java.util.Comparator;
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

    public boolean isActiveNow() {
        return isActiveNow;
    }


    public void activate() {
        if (!used && !isActiveNow) {
            isActiveNow = true;
        }
    }

    public void active(Zombie[] zombies) {
        activate();
    }



    public void update(GameSession gameSession){
        if (!isActiveNow || used) {
            return;
        }

        double previousX = positionX;
        double newX = previousX + Constants.LAWNMOWER_SPEED;

        List<Zombie> zombies = new ArrayList<>(
                gameSession.getMap().getRows().get(row).getZombies()
        );
        for (Zombie zombie : zombies) {
            if (zombie.getHealth().isDead()) {
                continue;
            }

            double zombieX = zombie.getMovement().getPositionX();
            if (collidesWithMovementSegment(zombieX, previousX, newX)) {
                zombie.getHealth().applyDamage(zombie.getHealth().getTotalHP(), null);
            }
        }

        positionX = newX;

        if (positionX > Constants.LAWNMOWER_END_POSITION) {
            used = true;
            isActiveNow = false;
        }
    }


    private boolean collidesWithMovementSegment(double zombieX, double segmentStart, double segmentEnd) {
        return zombieX >= segmentStart && zombieX <= segmentEnd;
    }

    public void setPositionX(double positionX) {
        this.positionX = positionX;
    }
}
