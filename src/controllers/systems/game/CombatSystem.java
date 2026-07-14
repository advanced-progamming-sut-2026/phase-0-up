package controllers.systems.game;

import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Lawnmower;
import models.map.Row;
import utils.Constants;

import java.util.List;

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
    private void checkLawnmowers(GameSession session){
        for (Row row : session.getMap().getRows()){
            Lawnmower lawnmower = row.getLawnmower();

            if(lawnmower.isUsed()){
                continue;
            }
            if(!lawnmower.isActiveNow()){
                tryActivateLawnmower(lawnmower, row.getZombies());
            }
        }
    }
    private void tryActivateLawnmower(Lawnmower lawnmower , List<Zombie> zombies){
        for(Zombie z : zombies){
            if (z.getHealth().isDead()){
                continue;
            }
            if(z.getMovement().getPositionX() <= Constants.LAWNMOWER_ACTIVATION_THRESHOLD){
                lawnmower.activate();
                return;
            }
        }
    }
    private void processDeaths(GameSession session){
        //TODO: call onDeath method and then remove.
    }
    private void updateZombieStates(GameSession session){}
}
