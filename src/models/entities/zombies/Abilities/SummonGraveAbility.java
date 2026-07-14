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

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getGameSession() == null || zombie.getGameSession().getMap() == null) {
            return;
        }

        if (zombie.getState().isUnableToMove()) {
            return;
        }
        tickCounter++;
        if (tickCounter >= COOLDOWN_TICKS) {
            spawnRandomGraves(zombie.getGameSession());
            tickCounter = 0;
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
            System.out.println("Tomb Raiser added a grave terrain at X: " + targetCell.getX() + ", Row: " + targetCell.getY());
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