package controllers.systems;

import models.game.GameSession;

//this class should resolve the combat mechanics
public class CombatSystem {

    public void processTick(GameSession session, long currentTick) {
        zombieAttack(session);
        plantAttack(session, currentTick);
        resolveProjectiles(session);
    }


    public void resolve(GameSession gameSession){}
    private void zombieAttack(GameSession gameSession){}
    private void plantAttack(GameSession gameSession, long currentTick){}
    private void resolveProjectiles(GameSession session){};
    private void checkLawnmowers(GameSession session){}
    private void processDeaths(GameSession session){}
    private void updateZombieStates(GameSession session){}
}
