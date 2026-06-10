package models.minigames;

import models.entities.zombies.Zombie;
import models.game.GameSession;

public class Zombotany extends Minigame{
    Zombie zombie; //from ZombotanyZombies

    @Override
    public void start(GameSession s) {

    }

    @Override
    public boolean checkWin(GameSession s) {
        return false;
    }
}
