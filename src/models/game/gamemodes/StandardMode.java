package models.game.gamemodes;

import models.game.GameSession;

//TODO: implement StandardMode
public class StandardMode implements GameMode {
    @Override
    public void onStart(GameSession gameSession) {

    }

    @Override
    public void onTick(GameSession gameSession) {

    }

    @Override
    public boolean checkWin(GameSession gameSession) {
        return false;
    }

    @Override
    public boolean checkLose(GameSession gameSession) {
        return false;
    }

    @Override
    public boolean requiresSeedSelection(GameSession gameSession) {
        return false;
    }

    @Override
    public boolean isCommandAllowed(String commandType) {
        return false;
    }
}
