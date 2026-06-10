package models.minigames;

import models.entities.intractables.Vase;
import models.game.GameSession;

public class VaseBreaker extends Minigame{
    public void breakVase(Vase vase, int x, int y){};

    @Override
    public void start(GameSession s) {

    }

    @Override
    public boolean checkWin(GameSession s) {
        return false;
    }
}
