package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

public class TurnIntoCat implements ZombieAbility {

    private int tickCounter = 0;
    private static final int TICKS_PER_SECOND = 10;
    private static final int SPELL_COOLDOWN = 6 * TICKS_PER_SECOND;

    @Override
    public void execute(Zombie wizard) {
        if (wizard == null || wizard.getState().isUnableToMove()) {
            return;
        }

        Plant adjacentPlant = getAdjacentFreePlant(wizard);
        if (adjacentPlant != null) {
            adjacentPlant.turnIntoCat(wizard);
            return;
        }

        tickCounter++;
        if (tickCounter >= SPELL_COOLDOWN) {
            Plant target = findFrontmostFreePlant(wizard);

            if (target != null) {
                target.turnIntoCat(wizard);
                tickCounter = 0;
            }
        }
    }

    private Plant getAdjacentFreePlant(Zombie wizard) {
        if (wizard.getGameSession() == null || wizard.getGameSession().getMap() == null) return null;

        int rowIdx = wizard.getMovement().getPositionY();
        double zX = wizard.getMovement().getPositionX();
        Row row = wizard.getGameSession().getMap().getRow(rowIdx);
        if (row == null || row.getCells() == null) return null;

        for (Cell cell : row.getCells()) {
            if (cell != null && cell.hasPlant()) {
                Plant p = cell.getCurrentPlant();
                // اگر گیاه در فاصله بسیار نزدیک باشد و هنوز گربه نشده باشد
                if (p != null && !p.isDead() && !p.isCat() && Math.abs(zX - cell.getX()) <= 35.0) {
                    return p;
                }
            }
        }
        return null;
    }

    private Plant findFrontmostFreePlant(Zombie wizard) {
        if (wizard.getGameSession() == null || wizard.getGameSession().getMap() == null) return null;

        int rowIdx = wizard.getMovement().getPositionY();
        Row row = wizard.getGameSession().getMap().getRow(rowIdx);
        if (row == null || row.getCells() == null) return null;

        for (int col = 8; col >= 0; col--) {
            Cell cell = row.cellAt(col);
            if (cell != null && cell.hasPlant()) {
                Plant p = cell.getCurrentPlant();
                if (p != null && !p.isDead() && !p.isCat() && !p.isFrozen() && !p.hasOctopus()) {
                    return p;
                }
            }
        }
        return null;
    }
}