package models.game.scoring;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;

// How the combat loop announces a kill to whoever is scoring it, without knowing that a scorer exists.
//
// CombatSystem.reportZombieDeath is the single point every zombie death passes through -- wave kills,
// mower kills, explosions, the nuke cheat -- so one notification here covers them all. A game mode that
// wants to score kills implements this; every other mode is untouched and the tick loop gains nothing.
public interface ZombieDeathListener {

    // Called once, the moment a zombie is confirmed dead and before it leaves the board.
    // `killer` is the plant credited with the kill, or null when nothing player-owned did it
    // (a lawn mower, poison, radioactive sun).
    void onZombieKilled(GameSession session, Zombie zombie, Plant killer, long tick);
}
