package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.game.GameSession;
import models.map.Cell;
import models.map.GameMap;
import models.map.Terrains.Terrain;
import utils.Constants;

import java.util.Iterator;

// Warmth aura: periodically melts frozen terrain in a tile area around the plant (fire plants like Wasabi Whip).
public class WarmthAbility extends PlantAbility {
    private int rowRadius;
    private int colRadius;

    public WarmthAbility(int actionInterval, TriggerStrategy triggerStrategy, int rowRadius, int colRadius) {
        super(actionInterval, triggerStrategy);
        this.rowRadius = rowRadius;
        this.colRadius = colRadius;
    }

    // Upgrade (MELT_AREA_3X3): widens the thaw aura to the surrounding tiles (Hot Potato).
    public void setRadius(int newRowRadius, int newColRadius) {
        this.rowRadius = newRowRadius;
        this.colRadius = newColRadius;
    }

    // Upgrade (WARM_RADIUS_EXT): extends the warmth aura outward (Pepper-pult).
    public void increaseRadius(int delta) {
        this.rowRadius += delta;
        this.colRadius += delta;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        GameMap map = gameSession.getMap();
        int ownerCol = (int) owner.getX();

        for (int rowOffset = -rowRadius; rowOffset <= rowRadius; rowOffset++) {
            int row = owner.getY() + rowOffset;
            if (row < 0 || row >= Constants.BOARD_ROWS) continue;

            for (int colOffset = -colRadius; colOffset <= colRadius; colOffset++) {
                int col = ownerCol + colOffset;
                if (col < 0 || col >= Constants.BOARD_COLS) continue;

                thaw(map.getRow(row).cellAt(col));
            }
        }
    }

    // Fire melts frozen terrain, which in turn thaws the plant trapped inside it.
    private void thaw(Cell cell) {
        Iterator<Terrain> iterator = cell.getTerrain().iterator();
        while (iterator.hasNext()) {
            Terrain t = iterator.next();
            t.takeDamage(0, Element.FIRE);
            if (t.isDestroyed()) {
                iterator.remove();
            }
        }
    }
}
