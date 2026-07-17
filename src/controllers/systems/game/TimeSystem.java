package controllers.systems.game;

import models.game.GameSession;

public class TimeSystem {
    private long totalTicks = 0;

    public void advance(GameSession gameSession, int ticks) {
        for (int i = 0; i < ticks; i++) {
            totalTicks++;
        }
    }

    public long getElapsedTime() {
        return totalTicks;
    }

    public double getElapsedTimeInSeconds() {
        return totalTicks / 10.0;
    }

    public void reset() {
        this.totalTicks = 0;
    }
}