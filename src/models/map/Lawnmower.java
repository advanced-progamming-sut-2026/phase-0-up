package models.map;

import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// A one-use machine parked at the end of a row. The first zombie to reach the end sets it off; it then
// drives back up the row, killing every zombie it passes, and is spent once it leaves the board. A
// second zombie reaching the end of that row has nothing left to stop it, and the level is lost
// (StandardMode.checkLose).
public class Lawnmower {
    private boolean used;
    private int row;
    private double positionX;
    private boolean isActiveNow;

    // Everything mown over the whole run, reported in one go when the mower leaves the board.
    private final List<Zombie> killed = new ArrayList<>();

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

    public double getPositionX() {
        return positionX;
    }

    // Starts the mower rolling from the end of the row. Ignored if it is already running or spent.
    public void activate() {
        if (!used && !isActiveNow) {
            isActiveNow = true;
            positionX = 0;
        }
    }

    // Advances the mower one tick, killing whatever it has driven past.
    //
    // Returns its full kill list on the tick it leaves the board -- that is when the run is over and
    // there is a complete list to report -- and an empty list on every other tick.
    public List<Zombie> update(GameSession gameSession) {
        if (!isActiveNow || used) {
            return Collections.emptyList();
        }

        double newX = positionX + Constants.LAWNMOWER_SPEED;

        List<Zombie> rowZombies = gameSession.getMap().getRow(row).getZombies();
        for (Zombie zombie : new ArrayList<>(rowZombies)) {
            // Only isDead() here, never isTargetable(): the zombie that set the mower off has stepped
            // past x = 0 and so counts as off the board, and it must be mown like the rest.
            if (zombie.getHealth().isDead()) {
                continue;
            }
            if (hasPassed(zombie.getMovement().getPositionX(), newX)) {
                zombie.getHealth().applyDamage(zombie.getHealth().getTotalHP(), Element.NEUTRAL, null);
                // The mower owns what it mows: pull it off the row now so processDeaths never reports
                // it separately, and hold it for the single summary printed when the run ends. The
                // zombie keeps its position, so its death line reads the spot it was struck.
                rowZombies.remove(zombie);
                killed.add(zombie);
            }
        }

        positionX = newX;

        if (positionX > Constants.LAWNMOWER_END_POSITION) {
            used = true;
            isActiveNow = false;
            return List.copyOf(killed);
        }
        return Collections.emptyList();
    }

    // Has the mower driven past this zombie? Everything from the left edge up to the mower's leading
    // edge counts, not just what falls inside this tick's step: the zombie that triggered the mower
    // breached past x = 0 and so sits behind its starting position, and it must still be mown.
    private boolean hasPassed(double zombieX, double leadingEdge) {
        return zombieX <= leadingEdge;
    }
}
