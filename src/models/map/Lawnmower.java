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

    // Whatever this tick's step drove over. Rebuilt each tick rather than accumulated over the run --
    // see update().
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

    // Advances the mower one tick, killing whatever it has driven past, and returns what it killed
    // ON THIS TICK.
    //
    // ## It used to hold the whole run back
    //
    // The kill list was accumulated across the run and returned only on the tick the mower drove off
    // the board, so that the summary could be printed in one go. The zombies themselves were removed
    // from the row the instant they were struck, but nothing SAID they had died until the run ended --
    // and the death sentence is the only thing the view has to play a death on (the model deletes a
    // zombie the tick it dies; see DeathEffects).
    //
    // A mower crosses at LAWNMOWER_SPEED to LAWNMOWER_END_POSITION -- 0.6 a tick over nine cells, so
    // fifteen ticks, a second and a half. Everything it mowed therefore vanished on contact and then
    // died on screen up to a second and a half later, in a heap, next to a mower that had already gone.
    // Reporting per tick is what puts the death back on the frame the blade reaches it.
    public List<Zombie> update(GameSession gameSession) {
        if (!isActiveNow || used) {
            return Collections.emptyList();
        }
        killed.clear();

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
                // it separately. The zombie keeps its position, so its death line reads the spot it was
                // struck -- which, now that the line goes out on the same tick, is also where the mower
                // is standing when the body appears.
                rowZombies.remove(zombie);
                killed.add(zombie);
            }
        }

        positionX = newX;

        if (positionX > Constants.LAWNMOWER_END_POSITION) {
            used = true;
            isActiveNow = false;
        }
        return List.copyOf(killed);
    }

    // Has the mower driven past this zombie? Everything from the left edge up to the mower's leading
    // edge counts, not just what falls inside this tick's step: the zombie that triggered the mower
    // breached past x = 0 and so sits behind its starting position, and it must still be mown.
    private boolean hasPassed(double zombieX, double leadingEdge) {
        return zombieX <= leadingEdge;
    }
}
