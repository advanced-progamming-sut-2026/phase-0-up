package controllers.systems.game;

import models.game.GameSession;
import utils.Constants;

// Drives the game clock.
//
// The session owns the tick count, so this system advances that counter rather than keeping a private
// one of its own: SeedPacket recharge, the WaveSystem's wave delays and SunSystem's drop rate all read
// GameSession.getTimeTicks(), and a second counter here could only ever drift away from the number
// everything else is actually using.
public class TimeSystem {

    public void advance(GameSession gameSession, int ticks) {
        if (gameSession == null || ticks <= 0) {
            return;
        }
        gameSession.advanceTime(ticks);
    }

    public long getElapsedTime(GameSession gameSession) {
        return gameSession == null ? 0 : gameSession.getTimeTicks();
    }

    public double getElapsedTimeInSeconds(GameSession gameSession) {
        return getElapsedTime(gameSession) / (double) Constants.TICKS_PER_SECOND;
    }
}
