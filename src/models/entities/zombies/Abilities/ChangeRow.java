package models.entities.zombies.Abilities;

import models.entities.zombies.Zombie;
import models.map.Row;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChangeRow implements ZombieAbility {
    private int tickCounter = 0;
    private static final int TICKS_PER_SECOND = 10;
    private static final int DANCE_INTERVAL_TICKS = 4 * TICKS_PER_SECOND;

    private final Random random = new Random();

    @Override
    public void execute(Zombie pianist) {
        if (pianist.getState().isUnableToMove()) {
            return;
        }
        tickCounter++;
        if (tickCounter >= DANCE_INTERVAL_TICKS) {
            triggerLaneSwitchForRow(pianist);
            tickCounter = 0;
        }
    }

    private void triggerLaneSwitchForRow(Zombie pianist) {
        int currentRow = pianist.getMovement().getPositionY();

        List<Zombie> allZombies = new ArrayList<>();
        for(Row row : pianist.getGameSession().getMap().getRows()){
            for(Zombie z : row.getZombies()){
                allZombies.add(z);
            }
        }

        for (Zombie targetZombie : allZombies) {
            if (targetZombie != pianist &&
                    targetZombie.getMovement().getPositionY() == currentRow &&
            !targetZombie.getMovement().isSwitchingLane() &&
            !targetZombie.getState().isUnableToMove()) {
                int newLaneY = getRandomNeighborLane(currentRow);
                if (newLaneY != currentRow) {
                    targetZombie.getMovement().startLaneSwitch(newLaneY);
                }
            }
        }
    }

    private int getRandomNeighborLane(int currentLane) {
        int maxLanes = 5;

        boolean canGoUp = (currentLane > 0);
        boolean canGoDown = (currentLane < maxLanes - 1);

        if (canGoUp && canGoDown) {
            return random.nextBoolean() ? currentLane - 1 : currentLane + 1;
        } else if (canGoUp) {
            return currentLane - 1;
        } else if (canGoDown) {
            return currentLane + 1;
        }

        return currentLane;
    }
}