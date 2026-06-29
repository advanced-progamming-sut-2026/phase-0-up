package models.map;

import models.entities.zombies.Zombie;

public class Lawnmower {
    private boolean used;
    private int row;

    public Lawnmower(int row) {
        used = false;
        this.row = row;
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

    public void Active(Zombie[] zombies){};
}
