package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Components.ActionState;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Cell;
import models.map.Row;

// Turquoise Zombie: "if it sees a plant within a 4-tile radius it starts stealing the sun STORED BY THE
// PLAYER, 25 sun a second, and keeps that up for 5 seconds. After the 5 seconds it fires a powerful
// laser at the four tiles ahead in its row... after it is killed it drops HALF the sun it stole."
// (documents/project.md line 1183.)
//
// This drains the player's bank. It is NOT the Ra Zombie's theft -- Ra never touches the bank, it drags
// loose sun tokens off the lawn (StealGroundSunAbility). The two were once the same class behind a
// flag, which gave Ra access to a bank the spec never grants it.
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
    private int totalStolenSun = 0;

    public StealSunAbility(double searchRadius, int sunPerSecond, GameSession gameSession) {
        this.searchRadius = searchRadius;
        this.sunPerSecond = sunPerSecond;
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
                    gameSession.reportEvent("The " + zombie.getAlias() + " spots a plant and starts "
                            + "siphoning your sun.");
                }
                break;

            case STEALING:
                totalStealTicks++;
                oneSecondTicks++;

                if (oneSecondTicks >= TICKS_PER_SECOND) {
                    oneSecondTicks = 0;
                    int stolen = stealFromPlayer(sunPerSecond, gameSession);
                    totalStolenSun += stolen;
                    if (stolen > 0) {
                        gameSession.reportEvent("The " + zombie.getAlias() + " steals " + stolen
                                + " sun; you have " + gameSession.getSunAmount() + " sun now.");
                    }
                }

                // Five seconds up: the heist is over and finishing it is what arms the laser.
                if (totalStealTicks >= MAX_STEAL_TICKS) {
                    currentState = StealState.FINISHED;
                    zombie.getState().setAction(ActionState.WALKING);
                    zombie.getState().setReadyForLaser(true);
                }
                break;

            case FINISHED:
                // LaserBeamAbility clears the flag when the beam fires; that is the cue to line up
                // the next heist.
                if (!zombie.getState().isReadyForLaser()) {
                    currentState = StealState.SEARCHING;
                }
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

    // "After it is killed it drops HALF the sun it stole." Ra, by contrast, returns all of its haul --
    // see StealGroundSunAbility.
    public int getSunDropAmountOnDeath() {
        return totalStolenSun / 2;
    }

    public int getTotalStolenSun() {
        return totalStolenSun;
    }
}