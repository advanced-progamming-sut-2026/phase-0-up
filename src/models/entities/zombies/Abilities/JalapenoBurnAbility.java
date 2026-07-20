package models.entities.zombies.Abilities;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.map.Cell;
import models.map.Row;
import utils.Constants;

// Zombotany Jalapeno zombie: if it survives about 10 seconds after entering the garden, it ignites its
// whole lane -- every plant in the row is destroyed and the zombie is consumed in the blast. Killing it
// before the fuse runs out prevents the burn (a dead zombie never ticks its abilities).
public class JalapenoBurnAbility implements ZombieAbility {
    private static final int FUSE_TICKS = 10 * Constants.TICKS_PER_SECOND;

    private int onBoardTicks;
    private boolean detonated;

    @Override
    public void execute(Zombie zombie) {
        if (detonated || zombie == null || zombie.getHealth().isDead() || !zombie.isOnBoard()) {
            return;   // the fuse only burns while the zombie is alive and on the lawn
        }
        onBoardTicks++;
        if (onBoardTicks < FUSE_TICKS) {
            return;
        }
        detonated = true;
        Row row = zombie.getGameSession().getMap().getRow(zombie.getMovement().getPositionY());
        if (row != null) {
            for (Cell cell : row.getCells()) {
                destroy(cell.getCurrentPlant());
                destroy(cell.getProtector());
            }
        }
        zombie.getHealth().applyDamage(zombie.getHealth().getTotalHP(), Element.FIRE, null);
    }

    private void destroy(Plant plant) {
        if (plant != null && !plant.isDead() && plant.getHealth() != null) {
            plant.getHealth().takeDamage(Integer.MAX_VALUE);
        }
    }
}
