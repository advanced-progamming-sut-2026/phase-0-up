package models.entities.zombies.Abilities;

import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Cell;
import models.map.Row;
import models.map.Terrains.GraveTerrain;
import models.map.Terrains.NormalGrave;
import models.map.Terrains.Terrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SummonGraveAbility implements ZombieAbility {
    private int tickCounter = 0;
    private static final int TICKS_PER_SECOND = 10;
    private static final int COOLDOWN_TICKS = 7 * TICKS_PER_SECOND;

    private static final int GRAVES_PER_CAST = 2;

    // The Tomb Raiser CHANTS first, and the stones come up when it finishes.
    //
    // Graves used to appear on the same tick the cooldown expired, which left the zombie's own art with
    // nothing to do: a headstone simply existed on the next frame, several tiles from a zombie that had
    // not moved a muscle. So the cast is now two moments -- the chant starts, and the ground answers --
    // and the view has something to hang the animation on in between.
    //
    // 30 ticks is the length of ZOMBIE_EGYPT_TOMBRAISER's `power` clip, which is 3 seconds at 10 Hz.
    // The two numbers ARE the same duration by construction, exactly as GlobalTargetingAbility's
    // wind-up and its strike effect are: change one and the other has to move with it, or the stones
    // come up while the zombie is still winding its arm back.
    private static final int CHANT_TICKS = 3 * TICKS_PER_SECOND;

    // Ticks into the chant, or NOT_CHANTING when the raiser is just walking.
    private static final int NOT_CHANTING = -1;
    private int chantTicks = NOT_CHANTING;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getGameSession() == null || zombie.getGameSession().getMap() == null) {
            return;
        }

        if (zombie.getState().isUnableToMove()) {
            // Frozen, buttered or otherwise held: the chant breaks and the cooldown starts again. A
            // zombie that cannot move cannot finish raising anything, and letting the count survive
            // would have the stones come up out of a zombie standing frozen in a block of ice.
            chantTicks = NOT_CHANTING;
            return;
        }
        if (chantTicks != NOT_CHANTING) {
            advanceChant(zombie);
            return;
        }
        tickCounter++;
        if (tickCounter >= COOLDOWN_TICKS) {
            tickCounter = 0;
            chantTicks = 0;
            // What the view listens for to play `power`. The tiles are deliberately NOT chosen yet --
            // three seconds is long enough for the board to change, and picking them now could put a
            // headstone on a tile a player has planted in the meantime.
            zombie.getGameSession().reportEvent("The Tomb Raiser starts chanting for the dead.");
        }
    }

    private void advanceChant(Zombie zombie) {
        chantTicks++;
        if (chantTicks >= CHANT_TICKS) {
            chantTicks = NOT_CHANTING;
            spawnRandomGraves(zombie.getGameSession());
        }
    }

    private void spawnRandomGraves(GameSession gameSession) {
        List<Cell> emptyCells = new ArrayList<>();

        for (Row row : gameSession.getMap().getRows()) {
            if (row != null && row.getCells() != null) {
                for (Cell cell : row.getCells()) {
                    if (cell != null && isCellValidForGrave(cell)) {
                        emptyCells.add(cell);
                    }
                }
            }
        }

        if (emptyCells.isEmpty()) {
            return;
        }
        Collections.shuffle(emptyCells);
        int gravesToSpawn = Math.min(GRAVES_PER_CAST, emptyCells.size());

        for (int i = 0; i < gravesToSpawn; i++) {
            Cell targetCell = emptyCells.get(i);
            targetCell.addTerrain(new NormalGrave(gameSession , targetCell));
            gameSession.reportEvent("The Tomb Raiser raises a grave at ("
                    + (int) targetCell.getX() + ", " + targetCell.getY() + ").");
        }
    }

    private boolean isCellValidForGrave(Cell cell) {
        if (!cell.isPlantable() || cell.isFlooded()) {
            return false;
        }
        if (cell.getCurrentPlant() != null || cell.getProtector() != null) {
            return false;
        }

        if (cell.getTerrain() != null) {
            for (Terrain t : cell.getTerrain()) {
                if (t instanceof GraveTerrain && !t.isDestroyed()) {
                    return false;
                }
            }
        }

        return true;
    }
}