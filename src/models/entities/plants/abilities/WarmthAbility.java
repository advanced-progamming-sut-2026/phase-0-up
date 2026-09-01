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

    // Ticks the plant spends visibly working on the ice before it gives way.
    //
    // Hot Potato's whole job is this, and the art ships it a four-and-a-half second `attack` clip that
    // nothing could ask for: with no wind-up the block melted on the tick the potato landed, so the
    // player saw a potato appear, an ice block vanish, and no connection drawn between the two.
    private static final int WIND_UP_TICKS = 20;
    private int windUpRemaining = -1;

    @Override
    public boolean isWindingUp() {
        return windUpRemaining >= 0;
    }

    // Only when there is actually ice in reach. A warmth aura that claimed to be working at all times
    // would put every fire plant into its attack pose permanently -- Wasabi Whip and Pepper-pult carry
    // one of these too, and they have other things to be doing.
    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        if (windUpRemaining >= 0 || !hasFrozenInRange(owner, gameSession)) {
            return false;
        }
        return super.canExecute(owner, gameSession);
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        if (windUpRemaining > 0) {
            windUpRemaining--;
        } else if (windUpRemaining == 0) {
            windUpRemaining = -1;
            thawArea(owner, gameSession);
        }
        super.update(owner, gameSession);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        windUpRemaining = WIND_UP_TICKS;
    }

    // Whether the plant is used up by the thaw. Hot Potato is one thing that happens once -- it goes on
    // the block, the block melts, and the potato is spent. A plant that radiates warmth as a side line
    // (Wasabi Whip, Pepper-pult) has other work to do and stays where it is.
    private boolean consumedOnUse;

    public void setConsumedOnUse(boolean consumedOnUse) {
        this.consumedOnUse = consumedOnUse;
    }

    private void thawArea(Plant owner, GameSession gameSession) {
        // Collected first, hit once.
        //
        // A FIRE hit shatters one block outright (HealthComponent.strikeIce), so hitting inside the
        // per-cell loop cost a Troglobite one block per TILE its wall overlapped -- three at a stride,
        // and a different number from one tick to the next as the wall slid across the tile boundaries.
        // The whole wall could evaporate from a single thaw, and it looked like the blocks were being
        // damaged by moving, because in effect they were: what changed between the ticks that took one
        // block and the ticks that took three was only the zombie's position.
        //
        // One thaw is one blow. Identity-keyed, because two Troglobites in range are two separate
        // zombies to hit and the same one seen from three tiles is not.
        java.util.Set<models.entities.zombies.Zombie> pushers =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        forEachCellInRange(owner, gameSession, cell -> {
            thaw(cell);
            pushers.addAll(pushedBlocksOn(cell, gameSession));
        });
        for (models.entities.zombies.Zombie pusher : pushers) {
            pusher.getHealth().applyDamage(0, Element.FIRE, owner);
        }
        gameSession.reportEvent("The " + owner.getName() + " thaws the ice at ("
                + (int) owner.getX() + ", " + owner.getY() + ") into a puddle"
                + (consumedOnUse ? " and burns itself out!" : "!"));

        if (consumedOnUse && owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }

    private boolean hasFrozenInRange(Plant owner, GameSession gameSession) {
        boolean[] found = {false};
        forEachCellInRange(owner, gameSession, cell -> {
            for (Terrain t : cell.getTerrain()) {
                if (t instanceof models.map.Terrains.FrozenTerrain && !t.isDestroyed()) {
                    found[0] = true;
                    return;
                }
            }
            if (!pushedBlocksOn(cell, gameSession).isEmpty()) {
                found[0] = true;
            }
        });
        return found[0];
    }

    // The Troglobites whose wall of ice covers this tile.
    //
    // A Troglobite's blocks are the third kind of ice in this game -- alongside an authored '&' and the
    // block a snowball puts a plant in -- and the only one that MOVES, so it is not terrain and cannot
    // be found by looking at the cell. Without this, Hot Potato planted directly on the wall melted
    // nothing at all, which is the one counter the game names for this zombie.
    private java.util.List<models.entities.zombies.Zombie> pushedBlocksOn(Cell cell,
                                                                         GameSession gameSession) {
        java.util.List<models.entities.zombies.Zombie> pushers = new java.util.ArrayList<>();
        java.util.List<models.entities.zombies.Zombie> lane =
                gameSession.getMap().getRow(cell.getY()).getZombies();
        if (lane == null) {
            return pushers;
        }
        for (models.entities.zombies.Zombie zombie : lane) {
            if (models.entities.zombies.Abilities.PushIceAbility.coversTile(zombie, cell.getX())) {
                pushers.add(zombie);
            }
        }
        return pushers;
    }

    private void forEachCellInRange(Plant owner, GameSession gameSession,
                                    java.util.function.Consumer<Cell> action) {
        GameMap map = gameSession.getMap();
        int ownerCol = (int) owner.getX();

        for (int rowOffset = -rowRadius; rowOffset <= rowRadius; rowOffset++) {
            int row = owner.getY() + rowOffset;
            if (row < 0 || row >= Constants.BOARD_ROWS) continue;

            for (int colOffset = -colRadius; colOffset <= colRadius; colOffset++) {
                int col = ownerCol + colOffset;
                if (col < 0 || col >= Constants.BOARD_COLS) continue;

                action.accept(map.getRow(row).cellAt(col));
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
