package models.minigames;

import models.entities.zombies.Zombie;
import models.game.GameSession;

public class IZombie extends Minigame{
    public void placeZombie(Zombie zombie, int x, int y){};

    @Override
    public void start(GameSession s) {

    }

    @Override
    public boolean checkWin(GameSession s) {
        return false;
    }
}
