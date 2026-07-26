package models.minigames;

import models.game.GameSession;

// Base type for the mini-game model classes (VaseBreaker, IZombie, WallnutBowling, Zombotany). The
// playable rules live in models.game.gamemodes.*Mode, which is what GameSession actually drives.
// The level/difficulty fields declared here were never read by this class or any subclass -- each
// mode carries its own difficulty -- so they were removed for PMD's UnusedPrivateField rule.
public abstract class Minigame {
    public abstract void start(GameSession s);
    public abstract boolean checkWin(GameSession s);
}
