package models.entities.zombies.Abilities;

import models.entities.zombies.Zombie;
import models.map.Row;
import utils.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Pianist Zombie: while it plays, the zombies on the lawn swap their row with one of the neighbouring
// rows every few seconds (documents/project.md, Pianist Zombie). The music carries across the whole
// lawn, so every zombie on the board dances -- not just the ones sharing the pianist's lane.
//
// The pianist itself is exempt: it is pushing a piano down its lane and does not hop rows.
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
        // Nothing to play to until the piano has actually rolled onto the lawn.
        if (!pianist.isOnBoard()) {
            return;
        }
        tickCounter++;
        if (tickCounter >= DANCE_INTERVAL_TICKS) {
            triggerLaneSwitchForRow(pianist);
            tickCounter = 0;
        }
    }

    // Collects every dancer first and only then queues the hops. The switch itself is just a flag on
    // the movement component -- CombatSystem re-files the zombie into its new Row after the tick -- so
    // no row's zombie list is touched here and the sweep cannot trip over its own changes.
    private void triggerLaneSwitchForRow(Zombie pianist) {
        List<Zombie> dancers = new ArrayList<>();
        for (Row row : pianist.getGameSession().getMap().getRows()) {
            for (Zombie z : row.getZombies()) {
                if (z != pianist && z.isTargetable() && !z.getMovement().isSwitchingLane()
                        && !z.getState().isUnableToMove()) {
                    dancers.add(z);
                }
            }
        }

        int moved = 0;
        for (Zombie dancer : dancers) {
            int currentLane = dancer.getMovement().getPositionY();
            int newLaneY = getRandomNeighborLane(currentLane);
            if (newLaneY != currentLane) {
                dancer.getMovement().startLaneSwitch(newLaneY);
                moved++;
            }
        }

        if (moved > 0) {
            pianist.getGameSession().reportEvent("The pianist strikes up a tune and " + moved
                    + " zombie(s) dance into a neighbouring lane.");
        }
    }

    private int getRandomNeighborLane(int currentLane) {
        boolean canGoUp = (currentLane > 0);
        boolean canGoDown = (currentLane < Constants.BOARD_ROWS - 1);

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
