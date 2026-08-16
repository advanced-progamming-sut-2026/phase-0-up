package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.projectiles.Element;
import models.game.GameSession;
import models.map.Cell;
import models.map.Terrains.GraveTerrain;
import models.map.Terrains.Terrain;

import java.util.Iterator;

// Destroys the grave on the plant's own tile, then is consumed (Grave Buster).
public class GraveBusterAbility extends PlantAbility implements Striking {
    private boolean hasExecuted;

    // Grave Buster strikes its own tile rather than a distant zombie, but the view needs the same two
    // facts -- that it happened, and where -- to throw the dirt. See Striking.
    private int strikes;
    private double lastStrikeX;
    private double lastStrikeY;

    public GraveBusterAbility() {
        super(0, null);
        this.hasExecuted = false;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        return !hasExecuted;
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        hasExecuted = true;
        strikes++;
        lastStrikeX = owner.getX();
        lastStrikeY = owner.getY();

        Cell cell = gameSession.getMap().getRow(owner.getY()).cellAt((int) owner.getX());
        Iterator<Terrain> iterator = cell.getTerrain().iterator();
        while (iterator.hasNext()) {
            Terrain t = iterator.next();
            if (t instanceof GraveTerrain) {
                t.takeDamage(Integer.MAX_VALUE, Element.NEUTRAL);
                if (t.isDestroyed()) {
                    iterator.remove();
                }
            }
        }

        if (owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }

    @Override
    public int strikeCount() {
        return strikes;
    }

    @Override
    public double strikeX() {
        return lastStrikeX;
    }

    @Override
    public double strikeY() {
        return lastStrikeY;
    }
}
