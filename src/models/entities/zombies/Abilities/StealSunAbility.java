package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Components.ActionState;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Cell;
import models.map.Row;

public class StealSunAbility implements ZombieAbility {
    private enum StealState { SEARCHING, STEALING, FINISHED }
    private StealState currentState = StealState.SEARCHING;
    private final GameSession gameSession;

    private int totalStealTicks = 0;
    private int oneSecondTicks = 0;
    private static final int TICKS_PER_SECOND = 10;
    private static final int MAX_STEAL_TICKS = 5 * TICKS_PER_SECOND;
    private final double searchRadius;
    private final int sunPerSecond;
    private final boolean stopAfterMaxTime;
    private int totalStolenSun = 0;

    public StealSunAbility(double searchRadius, int sunPerSecond, boolean stopAfterMaxTime, GameSession gameSession) {
        this.searchRadius = searchRadius;
        this.sunPerSecond = sunPerSecond;
        this.stopAfterMaxTime = stopAfterMaxTime;
        this.gameSession = gameSession;
    }

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || gameSession == null) {
            return;
        }

        if (zombie.getState().isUnableToMove() && currentState != StealState.STEALING) {
            return;
        }

        switch (currentState) {
            case SEARCHING:
                if (isPlantInRadius(zombie, searchRadius, gameSession)) {
                    currentState = StealState.STEALING;
                    zombie.getState().setAction(ActionState.IDLE);
                    totalStealTicks = 0;
                    oneSecondTicks = 0;
                }
                break;

            case STEALING:
                if (!isPlantInRadius(zombie, searchRadius, gameSession) && !stopAfterMaxTime) {
                    currentState = StealState.SEARCHING;
                    if (zombie.getState().getCurrentAction() == ActionState.IDLE) {
                        zombie.getState().setAction(ActionState.WALKING);
                    }
                    break;
                }

                totalStealTicks++;
                oneSecondTicks++;

                if (oneSecondTicks >= TICKS_PER_SECOND) {
                    oneSecondTicks = 0;
                    int stolen = stealFromPlayer(sunPerSecond, gameSession);
                    totalStolenSun += stolen;
                }

                if (stopAfterMaxTime && totalStealTicks >= MAX_STEAL_TICKS) {
                    currentState = StealState.FINISHED;
                    zombie.getState().setReadyForLaser(true);
                }
                break;

            case FINISHED:
                break;
        }
    }

    private boolean isPlantInRadius(Zombie zombie, double radiusInTiles, GameSession gameSession) {
        if (zombie.getMovement() == null || gameSession.getMap() == null) {
            return false;
        }

        int zombieRow = zombie.getMovement().getPositionY();
        double zombieX = zombie.getMovement().getPositionX();

        Row row = gameSession.getMap().getRow(zombieRow);
        if (row == null || row.getCells() == null) {
            return false;
        }

        for (Cell cell : row.getCells()) {
            if (cell != null) {
                Plant plant = cell.getCurrentPlant();
                if (plant != null && !plant.isDead()) {
                    double distanceInTiles = Math.abs(zombieX - cell.getX());
                    if (distanceInTiles <= radiusInTiles) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private int stealFromPlayer(int amount, GameSession gameSession) {
        int currentSun = gameSession.getSunAmount();
        if (currentSun <= 0) {
            return 0;
        }

        int actualStolen = Math.min(currentSun, amount);

        gameSession.decreaseSunAmount(actualStolen);
        return actualStolen;
    }

    public int getSunDropAmountOnDeath() {
        if (!stopAfterMaxTime) {
            return totalStolenSun;
        } else {
            return totalStolenSun / 2;
        }
    }

    public int getTotalStolenSun() {
        return totalStolenSun;
    }
}