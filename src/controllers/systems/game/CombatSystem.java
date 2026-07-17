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
        checkLawnmowers(session);
    }


    public void resolve(GameSession gameSession){}
    private void zombieAttack(GameSession gameSession){}
    private void plantAttack(GameSession gameSession, long currentTick){}
    private void resolveProjectiles(GameSession session){};
    private void checkLawnmowers(GameSession session){
        for (Row row : session.getMap().getRows()){
            Lawnmower lawnmower = row.getLawnmower();

            if(lawnmower == null || lawnmower.isUsed()){
                continue;
            }
            if(!lawnmower.isActiveNow()){
                tryActivateLawnmower(lawnmower, row.getZombies());
            }
            // Drive a running mower -- including one that triggered on this very tick, so the zombie
            // that set it off is mown on the same tick rather than getting a free step first.
            lawnmower.update(session);
        }
    }
    // Deliberately tests only isDead(), NOT Zombie.isTargetable(): that rule also excludes zombies
    // off either end of the grid, and a breach is precisely the case where one has reached or stepped
    // past x = 0. Zombie speeds almost never land exactly on the threshold, so a breaching zombie is
    // usually already at a negative x -- guarding here would stop the mower from ever firing for it,
    // and StandardMode.checkLose only ends the level once the mower is spent, so the row would stall
    // with a zombie sitting past the house forever.
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
        //TODO: remove protector if dead.
    }
    private void updateZombieStates(GameSession session){}
}
