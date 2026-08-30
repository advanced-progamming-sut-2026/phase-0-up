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

    // How long it spends chewing. The grave used to be gone on the tick the plant landed, so a Grave
    // Buster appeared and vanished within a sixth of a second: its art is nothing BUT an attack clip
    // and none of it was ever seen. The view plays that clip through the chew.
    private static final int CHEW_TICKS = 20;
    private int chewRemaining = -1;

    @Override
    public boolean isWindingUp() {
        return chewRemaining >= 0;
    }

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        return !hasExecuted;
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        if (chewRemaining > 0) {
            chewRemaining--;
        } else if (chewRemaining == 0) {
            chewRemaining = -1;
            devour(owner, gameSession);
        }
        super.update(owner, gameSession);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        hasExecuted = true;
        chewRemaining = CHEW_TICKS;
    }

    // The grave goes when the chewing stops -- including the strike bookkeeping, so the dirt flies on
    // the frame the headstone actually breaks rather than the frame the plant landed.
    private void devour(Plant owner, GameSession gameSession) {
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
