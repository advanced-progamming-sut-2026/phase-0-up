package models.greenhouse;

import models.entities.plants.Plant;
import utils.regex.GreenHouseMenuRegex;

public class Pot{
    private int x;
    private int y;
    private PotState state;
    private long readyAtTimestamp;
    private GreenHousePlant onPot;

    public Pot(int x, int y) {
        this.x = x;
        this.y = y;
        this.state = PotState.LOCKED;
        this.readyAtTimestamp = 0;
        this.onPot = null;
    }

    public boolean isLocked(){
        return this.state == PotState.LOCKED;
    }

    public boolean isEmpty(){
        return this.state == PotState.EMPTY;
    }

    public boolean isReady(){
        if (this.state == PotState.GROWING && System.currentTimeMillis() >= readyAtTimestamp)
            state = PotState.READY;
        return state == PotState.READY;
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

    public PotState getState() {
        return state;
    }

    public void setState(PotState state) {
        this.state = state;
    }

    public GreenHousePlant getOnPot() {
        return onPot;
    }

    public void setOnPot(GreenHousePlant onPot) {
        this.onPot = onPot;
    }

    public long getReadyAtTimestamp() {
        return readyAtTimestamp;
    }

    public void setReadyAtTimestamp(long readyAtTimestamp) {
        this.readyAtTimestamp = readyAtTimestamp;
    }

    public int getRemainingHoursCeil() {
        if (isReady() || state != PotState.GROWING) return 0;

        long remainingMillis = readyAtTimestamp - System.currentTimeMillis();
        double remainingHours = (double) remainingMillis / (60 * 60 * 1000);
        return (int) Math.ceil(remainingHours);
    }

    public String getRemainingTimeFormatted() {
        if (state != PotState.GROWING) return "";
        long remainingMillis = readyAtTimestamp - System.currentTimeMillis();
        if (remainingMillis <= 0) return "Ready";

        long hours = remainingMillis / (60 * 60 * 1000);
        long minutes = (remainingMillis % (60 * 60 * 1000)) / (60 * 1000);
        return hours + "h " + minutes + "m left";
    }

    public void instantGrow() {
        this.readyAtTimestamp = System.currentTimeMillis();
        this.state = PotState.READY;
    }
}
