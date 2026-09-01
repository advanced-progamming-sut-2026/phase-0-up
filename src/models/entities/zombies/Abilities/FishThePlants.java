package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

// Fisherman Zombie: fixed in the rightmost column, dragging a plant one tile forward periodically.
public class FishThePlants implements ZombieAbility {
    private int tickCounter = 0;
    private static final int TICKS_PER_SECOND = 10;
    private static final int FISHING_COOLDOWN = 5 * TICKS_PER_SECOND;

    private static final double SHORELINE_X = utils.Constants.BOARD_COLS - 0.5;
    @Override
    public void execute(Zombie fisherman) {
        if (fisherman == null) {
            return;
        }
        anchorAtShoreline(fisherman);
        if (fisherman.getState().isUnableToMove()) {
            return;
        }
        if (castTicks != NOT_CASTING) {
            advanceCast(fisherman);
            return;
        }
        tickCounter++;
        if (tickCounter >= FISHING_COOLDOWN && hasSomethingToCatch(fisherman)) {
            tickCounter = 0;
            beginCast(fisherman);
        }
    }

    // The line goes out first and the plant comes in when it lands.
    //
    // 13 ticks is ZOMBIE_BEACH_FISHERMAN's `cast` clip, 1.267s at 10 Hz. Before this the plant simply
    // slid a tile sideways with the fisherman standing motionless -- it has `intro`, `cast`, `reel` and
    // `toss` and used none of them, which for a zombie whose entire behaviour is one gesture meant the
    // gesture was never drawn.
    private static final int CAST_TICKS = 13;
    private static final int NOT_CASTING = -1;
    private int castTicks = NOT_CASTING;

    private void beginCast(Zombie fisherman) {
        castTicks = 0;
        fisherman.getGameSession().reportEvent("The Fisherman Zombie casts its line at ("
                + (int) fisherman.getMovement().getPositionX() + ", "
                + fisherman.getMovement().getPositionY() + ").");
    }

    private void advanceCast(Zombie fisherman) {
        castTicks++;
        if (castTicks < CAST_TICKS) {
            return;
        }
        castTicks = NOT_CASTING;
        // Re-found rather than remembered: a second is long enough for the plant to be eaten, dug up
        // or shot, and the reel should take whatever is actually there when the hook lands. A cast
        // that catches nothing simply catches nothing -- the animation has already played.
        tryFishPlant(fisherman);
    }

    // Is there anything in this lane worth casting at? Cheap enough to ask every cooldown, and it stops
    // the fisherman miming a cast at an empty row for the whole level.
    private boolean hasSomethingToCatch(Zombie fisherman) {
        if (fisherman.getGameSession() == null || fisherman.getGameSession().getMap() == null) {
            return false;
        }
        Row row = fisherman.getGameSession().getMap().getRow(fisherman.getMovement().getPositionY());
        if (row == null || row.getCells() == null) {
            return false;
        }
        for (Cell cell : row.getCells()) {
            if (cell != null && cell.hasPlant() && !cell.getCurrentPlant().isDead()) {
                return true;
            }
        }
        return false;
    }

    private boolean anchored;

    // Speed 0, not an unable-to-move state (which would stop it fishing). Looks one step ahead.
    private void anchorAtShoreline(Zombie fisherman) {
        if (fisherman.getMovement() == null || anchored) {
            return;
        }
        double nextStep = fisherman.getMovement().getSpeed() * utils.Constants.ZOMBIE_SPEED_SCALE;
        if (fisherman.getMovement().getPositionX() - nextStep <= SHORELINE_X) {
            fisherman.getMovement().setPositionX(SHORELINE_X);
            fisherman.getMovement().setSpeed(0);
            anchored = true;
            // `intro` -- the fisherman settling in at the water's edge. Said once, on the tick it
            // stops: it is the only moment that clip belongs to and this is the only zombie in the
            // game that arrives somewhere and stays there.
            fisherman.getGameSession().reportEvent("The Fisherman Zombie wades in at ("
                    + (int) SHORELINE_X + ", " + fisherman.getMovement().getPositionY()
                    + ") and settles down to fish.");
        }
    }
    private boolean tryFishPlant(Zombie fisherman) {
        if (fisherman.getGameSession() == null || fisherman.getGameSession().getMap() == null) {
            return false;
        }

        int rowIdx = fisherman.getMovement().getPositionY();
        Row row = fisherman.getGameSession().getMap().getRow(rowIdx);
        if (row == null || row.getCells() == null) {
            return false;
        }
        for (int col = 7; col >= 0; col--) {
            Cell currentCell = row.cellAt(col);

            if (currentCell != null && currentCell.hasPlant()) {
                Plant targetPlant = currentCell.getCurrentPlant();

                if (targetPlant != null && !targetPlant.isDead()) {
                    if (col == 7) {
                        destroyHookedPlant(fisherman, currentCell, targetPlant);
                        return true;
                    }

                    Cell rightCell = row.cellAt(col + 1);
                    if (rightCell != null && !rightCell.hasPlant()) {
                        movePlantToRight(fisherman, currentCell, rightCell, targetPlant);
                        return true;
                    }
                    return false;
                }
            }
        }

        return false;
    }

    private void destroyHookedPlant(Zombie fisherman, Cell cell, Plant plant) {
        fisherman.getGameSession().reportEvent("The Fisherman Zombie hooks " + plant.getName()
                + " at (" + (int) cell.getX() + ", " + cell.getY() + ") and drags it into the ocean.");

        if (plant.getHealth() != null) {
            plant.getHealth().takeDamage(Integer.MAX_VALUE);
        }

        cell.removePlant();
    }

    private void movePlantToRight(Zombie fisherman, Cell fromCell, Cell toCell, Plant plant) {
        fromCell.removePlant();

        // The destination decides the plant's fate. Dragging a land plant onto open water with no Lily
        // Pad under it drowns it -- Cell.addPlant refuses that placement, and previously the plant was
        // simply dropped on the floor: gone from the board with no death, no event and no plants-lost
        // tally (so quests undercounted). Kill it properly instead, and only announce a move that
        // actually happened.
        if (!toCell.addPlant(plant).success()) {
            plant.getHealth().takeDamage(Integer.MAX_VALUE);
            fisherman.getGameSession().reportEvent("The Fisherman Zombie drags " + plant.getName()
                    + " off (" + (int) fromCell.getX() + ", " + fromCell.getY()
                    + ") and it drowns in the water.");
            fisherman.getGameSession().recordPlantLost();
            return;
        }
        fisherman.getGameSession().reportEvent("The Fisherman Zombie reels " + plant.getName()
                + " one tile to the right, to (" + (int) toCell.getX() + ", " + toCell.getY() + ").");
    }
}