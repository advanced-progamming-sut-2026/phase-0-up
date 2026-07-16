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

public abstract class GlobalTargetingAbility extends PlantAbility {
    private Random random;

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

    @Override
    public void update(Plant owner, GameSession gameSession) {
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

        applyEffectToTarget(targetZombie, owner, gameSession);
    }

    protected abstract void applyEffectToTarget(Zombie target, Plant owner, GameSession gameSession);

    protected List<Zombie> getValidTargets(GameSession gameSession) {
        List<Zombie> validTargets = new ArrayList<>();
        GameMap map = gameSession.getMap();

        for (int i = 0; i < Constants.BOARD_ROWS; i++){
            List<Zombie> zombiesInRow = map.getRow(i).getZombies();
            if (zombiesInRow != null){
                for (Zombie zombie : zombiesInRow){
                    if (!zombie.getHealth().isDead()){
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
