package models.minigames;

import models.game.GameSession;

public abstract class Minigame {
    private int level;
    private int difficulty;
    public abstract void start(GameSession s);
    public abstract boolean checkWin(GameSession s);


}
