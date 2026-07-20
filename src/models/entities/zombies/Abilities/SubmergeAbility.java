package models.entities.zombies.Abilities;

import models.entities.zombies.Components.ActionState;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;

public class SubmergeAbility implements ZombieAbility {

    @Override
    public void execute(Zombie snorkel) {
        if (snorkel == null || snorkel.getState() == null || snorkel.getMovement() == null) {
            return;
        }
        boolean isInWater = isZombieInWater(snorkel);
        boolean isEating = (snorkel.getState().getCurrentAction() == ActionState.EATING);
        if (isInWater && !isEating) {
            if (!snorkel.getState().isSubmerged()) {
                snorkel.getState().setSubmerged(true);
                snorkel.getGameSession().reportEvent("The Snorkel Zombie dives underwater at ("
                        + (int) snorkel.getX() + ", " + snorkel.getY() + ").");
            }
        } else {
            if (snorkel.getState().isSubmerged()) {
                snorkel.getState().setSubmerged(false);
                snorkel.getGameSession().reportEvent("The Snorkel Zombie surfaces at ("
                        + (int) snorkel.getX() + ", " + snorkel.getY() + ") and is vulnerable again.");
            }
        }
    }

    private boolean isZombieInWater(Zombie zombie) {
        if (zombie.getGameSession() == null || zombie.getGameSession().getMap() == null) {
            return false;
        }

        int rowIdx = zombie.getMovement().getPositionY();
        double zX = zombie.getMovement().getPositionX();

        Row row = zombie.getGameSession().getMap().getRow(rowIdx);
        if (row == null) return false;

        int colIdx = (int) zX;
        if (colIdx >= 0 && colIdx < row.getCells().size()) {
            Cell cell = row.cellAt(colIdx);
            if (cell != null) {
                return cell.isFlooded();
            }
        }

        return false;
    }
}