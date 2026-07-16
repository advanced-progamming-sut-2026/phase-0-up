package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;
import utils.Constants;

// Zombie Piano: rolls forward crushing any plant in its 2-row footprint for heavy damage (EatDPS 4000).
// Spiky plants (Spikeweed/Spikerock/Cactus) break the piano instead, destroying the zombie.
public class PianoCrushAbility implements ZombieAbility {
    private static final int CRUSH_DAMAGE = 4000;
    private static final double CRUSH_REACH = 1.0;

    @Override
    public void execute(Zombie zombie) {
        if (zombie == null || zombie.getState().isUnableToMove()) {
            return;
        }
        int row = zombie.getMovement().getPositionY();
        double zombieX = zombie.getMovement().getPositionX();
        // 2-row footprint: the piano's own lane and the lane above it
        crushLane(zombie, row, zombieX);
        crushLane(zombie, row - 1, zombieX);
    }

    private void crushLane(Zombie zombie, int row, double zombieX) {
        if (row < 0 || row >= Constants.BOARD_ROWS) {
            return;
        }
        Row r = zombie.getGameSession().getMap().getRow(row);
        if (r == null) {
            return;
        }
        for (Cell cell : r.getCells()) {
            Plant p = cell.getCurrentPlant();
            if (p == null || p.isDead() || Math.abs(cell.getX() - zombieX) > CRUSH_REACH) {
                continue;
            }
            if (isBreaker(p)) {
                zombie.getHealth().applyDamage(Integer.MAX_VALUE, Element.NEUTRAL, null);
                System.out.println("The piano smashed into a spiky plant and broke apart!");
                return;
            }
            if (p.getHealth() != null) {
                p.getHealth().takeDamage(CRUSH_DAMAGE);
            }
        }
    }

    private boolean isBreaker(Plant plant) {
        String name = plant.getName() == null ? "" : plant.getName();
        return name.contains("Spike") || name.contains("Cactus");
    }
}
