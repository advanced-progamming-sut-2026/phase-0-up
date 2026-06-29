package controllers.systems.game;

import models.game.GameSession;

public class TimeSystem {
    public void advance(GameSession gameSession, int ticks){};
    private long getElapsedTime(GameSession gameSession){return 0;}
}
