package models.map;

import models.entities.projectiles.Element;
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

        double newX = positionX + Constants.LAWNMOWER_SPEED;

        List<Zombie> zombies = new ArrayList<>(
                gameSession.getMap().getRows().get(row).getZombies()
        );
        for (Zombie zombie : zombies) {
            // Only isDead() here, never isTargetable(): the zombie that set the mower off has
            // usually stepped past x = 0 already, and that one must die like the rest.
            if (zombie.getHealth().isDead()) {
                continue;
            }

            if (hasReached(zombie.getMovement().getPositionX(), newX)) {
                zombie.getHealth().applyDamage(zombie.getHealth().getTotalHP(), Element.NEUTRAL,null);
            }
        }

        positionX = newX;

        if (positionX > Constants.LAWNMOWER_END_POSITION) {
            used = true;
            isActiveNow = false;
        }
    }


    // Everything from the left edge up to the mower's leading edge dies -- not just what falls inside
    // this tick's step. A strict [previousX, newX] window would spare the very zombie that triggered
    // the mower, since it breached past x = 0 and so sits behind the mower's starting position.
    // Sweeping the full row this way is also what the spec asks for: the mower kills every zombie in
    // its row by the time it leaves the board.
    private boolean hasReached(double zombieX, double leadingEdge) {
        return zombieX <= leadingEdge;
    }

    public void setPositionX(double positionX) {
        this.positionX = positionX;
    }
}
