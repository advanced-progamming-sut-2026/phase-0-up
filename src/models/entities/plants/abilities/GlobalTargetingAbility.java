package models.entities.plants.abilities;


import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.GameMap;
import utils.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public abstract class GlobalTargetingAbility extends PlantAbility implements Striking {
    private Random random;

    // Where the last hit landed, and how many there have been. See Striking: these abilities damage a
    // zombie outright with nothing in flight, so this is the only record the view can draw from.
    private int strikes;
    private double lastStrikeX;
    private double lastStrikeY;

    private TargetingPriority priorityStrategy;
    private double priorityRange;

    private int pendingBurstShots;
    private int burstTimer;
    private static final int BURST_INTERVAL = 2;
    private static final String GARGANTUAR_CATEGORY = "GARGANTUAR";

    private boolean prioritizeGargantuars;

    public GlobalTargetingAbility(int actionInterval, TriggerStrategy triggerStrategy,
                                  TargetingPriority priorityStrategy, double priorityRange) {
        super(actionInterval, triggerStrategy);
        this.random = new Random();
        this.priorityStrategy = priorityStrategy;
        this.priorityRange = priorityRange;
    }

    // Ticks between choosing a target and the effect landing on it.
    //
    // The same idea as ProduceSunAbility's WIND_UP_TICKS, and for the same reason: these abilities used
    // to damage their target on the very tick they fired, which left the view nothing to draw. A zombie
    // taking 5000 from an Electric Blueberry was dead before its cloud had left the plant, so the cloud
    // could only ever arrive at an empty tile. Holding the effect for half a second lets it FLY, and
    // the damage lands on the frame it arrives.
    //
    // The rate is untouched: PlantAbility resets the cooldown on execute(), so only the moment within
    // the cycle moves.
    private static final int WIND_UP_TICKS = 5;   // 0.5s at 10 ticks/sec

    // One entry per shot in the air. A LIST rather than a single slot because plant food queues a burst
    // two ticks apart -- far faster than the wind-up -- and a single slot would let each new shot
    // overwrite the one before it, so only the last of a Cat-tail's volley would ever land.
    private static final class Pending {
        private Zombie target;
        private int ticksLeft;
    }

    private final List<Pending> inFlight = new ArrayList<>();

    @Override
    public boolean isWindingUp() {
        return !inFlight.isEmpty();
    }

    // Lands every shot whose flight has run out. A target that died or submerged in the meantime is
    // dropped rather than struck: the effect is visibly arriving at something that is no longer there,
    // and damaging it would be hitting a corpse.
    private void landArrivals(Plant owner, GameSession gameSession) {
        java.util.Iterator<Pending> shots = inFlight.iterator();
        while (shots.hasNext()) {
            Pending shot = shots.next();
            shot.ticksLeft--;
            if (shot.ticksLeft > 0) {
                continue;
            }
            shots.remove();
            if (shot.target != null && shot.target.isTargetable()) {
                applyEffectToTarget(shot.target, owner, gameSession);
            }
        }
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        landArrivals(owner, gameSession);
        if (pendingBurstShots > 0) {
            if (burstTimer > 0) {
                burstTimer--;
            } else {
                execute(owner, gameSession);
                pendingBurstShots--;
                burstTimer = BURST_INTERVAL;
            }
        }
        super.update(owner, gameSession);
    }

    // Plant food: fires a rapid burst of `shots` homing hits (Cat-tail).
    public void queueBurst(int shots) {
        this.pendingBurstShots += shots;
    }

    // Upgrade (PRIORITIZE_GARGANTUARS): when a Gargantuar is on the board, target it first
    // (Electric Blueberry).
    public void setPrioritizeGargantuars(boolean prioritize) {
        this.prioritizeGargantuars = prioritize;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        List<Zombie> validTargets = getValidTargets(gameSession);
        if (validTargets.isEmpty()) return;

        Zombie targetZombie = null;


        if (priorityStrategy == TargetingPriority.HIGHEST_HP) {
            int maxHpFound = -1;

            for (Zombie z : validTargets) {
                int zHp = z.getHealth().getTotalHP();
                if (zHp > maxHpFound) {
                    maxHpFound = zHp;
                    targetZombie = z;
                }
            }
        }
        else if (priorityStrategy == TargetingPriority.CLOSEST_IN_RANGE && priorityRange > 0) {
            double minDistance = Double.MAX_VALUE;

            for (Zombie z : validTargets) {
                double distance = calculateDistance(owner, z);
                if (distance <= priorityRange && distance < minDistance) {
                    minDistance = distance;
                    targetZombie = z;
                }
            }
        }

        if (targetZombie == null) {
            int targetIndex = random.nextInt(validTargets.size());
            targetZombie = validTargets.get(targetIndex);
        }

        // Recorded at LAUNCH, which is also when the view starts drawing the effect on its way over.
        // The zombie barely moves in half a second, so where it was when the shot was aimed is where
        // the shot is drawn arriving.
        strikes++;
        lastStrikeX = targetZombie.getMovement().getPositionX();
        lastStrikeY = targetZombie.getMovement().getPositionY();

        // Held, not applied. landArrivals deals the damage when the flight runs out.
        Pending shot = new Pending();
        shot.target = targetZombie;
        shot.ticksLeft = WIND_UP_TICKS;
        inFlight.add(shot);
    }

    @Override
    public int strikeCount() {
        return strikes;
    }

    @Override
    public double strikeX() {
        return lastStrikeX;
    }

    @Override
    public double strikeY() {
        return lastStrikeY;
    }

    protected abstract void applyEffectToTarget(Zombie target, Plant owner, GameSession gameSession);

    protected List<Zombie> getValidTargets(GameSession gameSession) {
        List<Zombie> validTargets = new ArrayList<>();
        GameMap map = gameSession.getMap();

        for (int i = 0; i < Constants.BOARD_ROWS; i++){
            List<Zombie> zombiesInRow = map.getRow(i).getZombies();
            if (zombiesInRow != null){
                for (Zombie zombie : zombiesInRow){
                    if (zombie.isTargetable()){
                        validTargets.add(zombie);
                    }
                }
            }
        }

        if (prioritizeGargantuars) {
            List<Zombie> gargantuars = new ArrayList<>();
            for (Zombie zombie : validTargets) {
                if (GARGANTUAR_CATEGORY.equalsIgnoreCase(zombie.getCategory())) {
                    gargantuars.add(zombie);
                }
            }
            if (!gargantuars.isEmpty()) {
                return gargantuars;
            }
        }
        return validTargets;
    }

    private double calculateDistance(Plant owner, Zombie zombie) {
        double dx = Math.abs(zombie.getMovement().getPositionX() - owner.getX());
        double dy = Math.abs(zombie.getMovement().getPositionY() - owner.getY());
        return Math.sqrt((dx * dx) + (dy * dy));
    }
}
