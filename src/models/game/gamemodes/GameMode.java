package models.game.gamemodes;

import models.game.GameSession;

public interface GameMode {
    void onStart(GameSession gameSession);
    void onTick(GameSession gameSession);
    boolean checkWin(GameSession gameSession);
    boolean checkLose(GameSession gameSession);
    boolean requiresSeedSelection(GameSession gameSession);
    boolean isCommandAllowed(String commandType);
}
