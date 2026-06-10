package models.minigames;

import models.entities.plants.bowling.BowlingType;
import models.game.GameSession;
import models.map.Row;

public class WallnutBowling extends Minigame{
    public void placeNut(Row row, BowlingType type){};

    @Override
    public void start(GameSession s) {

    }

    @Override
    public boolean checkWin(GameSession s) {
        return false;
    }
}
