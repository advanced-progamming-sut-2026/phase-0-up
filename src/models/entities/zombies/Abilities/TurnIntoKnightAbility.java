package models.entities.zombies.Abilities;

import models.entities.zombies.Components.ActionState;
import models.entities.zombies.Components.ArmorType;
import models.entities.zombies.Components.HealthLayer;
import models.entities.zombies.Zombie;
import models.map.Row;
import utils.Constants;

// The King Zombie: it walks on, stops in the rightmost column, and stays there knighting its peasants.
//
// ## What it was doing instead
//
// Walking all the way down the lane like any other zombie. The spec is explicit that it takes the
// rightmost column and does not move, and nothing was pinning it -- zombies.json gives it the same
// 0.185 speed as everything else, so it strolled into the plants and started biting them. Its animation
// has no `walk` clip at all, which is the art saying the same thing: ClipMap fell through to `idle`, so
// it slid up the lawn in its standing pose.
//
// ## Its three clips
//
// ZOMBIE_DARK_KING ships `intro`, `idle`, `idle2`, `special` and `die`, and only `idle` and `die` were
// reachable. `intro` is the king arriving and `special` is the knighting itself; both are announced now
// so ZombieActions can play them.
public class TurnIntoKnightAbility implements ZombieAbility {

    private static final int TICKS_PER_SECOND = 10;

    // 2.5 seconds, which is zombies.json's own DelayBetweenKnightings for this zombie.
    private static final int KNIGHT_COOLDOWN = 25;

    // The knighting takes as long as the king's `special` clip -- 4 seconds at 10 Hz -- and the armour
    // appears when it ends. Same construction as SummonGraveAbility.CHANT_TICKS: change one and the
    // other has to move with it, or a peasant is knighted before the sceptre comes up.
    private static final int CAST_TICKS = 40;

    // Where the king stops: the centre of the rightmost column. Cells are built at `index + 0.5`, so
    // this is exactly the tile a plant in column 8 stands on.
    private static final double THRONE_X = Constants.BOARD_COLS - 0.5;

    // How far the king's word carries, from zombies.json's KnightingAreaX/Y (4 columns, 3 rows).
    private static final double KNIGHT_REACH_COLS = 4.0;
    private static final int KNIGHT_REACH_ROWS = 1;

    private int tickCounter = 0;
    private static final int NOT_CASTING = -1;
    private int castTicks = NOT_CASTING;
    private Zombie aimedAt;
    private boolean enthroned;

    @Override
    public void execute(Zombie king) {
        if (king == null || king.getState().isFrozen()
                || king.getState().getCurrentAction() == ActionState.DYING) {
            castTicks = NOT_CASTING;
            aimedAt = null;
            return;
        }

        if (!enthroned) {
            takeThrone(king);
            return;
        }

        if (castTicks != NOT_CASTING) {
            advanceCast(king);
            return;
        }

        tickCounter++;
        if (tickCounter >= KNIGHT_COOLDOWN) {
            Zombie target = findSimpleZombieNearby(king);
            if (target != null) {
                beginCast(king, target);
            }
        }
    }

    // Walks in as far as the rightmost column, then stops for good.
    //
    // The king is not pinned at birth: it spawns off the right edge like everything else, and a zombie
    // frozen there is off the board -- untargetable, unhittable and standing in the margin. It has to
    // walk on before it can take its place.
    private void takeThrone(Zombie king) {
        if (king.getMovement().getPositionX() > THRONE_X) {
            return;   // still walking in
        }
        enthroned = true;
        king.getMovement().setPositionX(THRONE_X);
        king.getMovement().setSpeed(0);
        // What the view listens for to play `intro`. Also the only warning the player gets that a king
        // has arrived, which is worth a line: everything else in the lane can be dealt with by killing
        // it, and this one keeps making the problem worse from a tile most plants cannot reach.
        king.getGameSession().reportEvent("The King Zombie takes his throne at ("
                + (int) THRONE_X + ", " + king.getMovement().getPositionY()
                + ") and starts handing out knighthoods.");
    }

    private void beginCast(Zombie king, Zombie target) {
        tickCounter = 0;
        castTicks = 0;
        aimedAt = target;
        // What the view listens for to play `special`. The king's own tile -- the armour has not
        // appeared yet, and what this has to find is the zombie raising its sceptre.
        king.getGameSession().reportEvent("The King Zombie raises his sceptre at ("
                + (int) king.getMovement().getPositionX() + ", "
                + king.getMovement().getPositionY() + ").");
    }

    private void advanceCast(Zombie king) {
        castTicks++;
        if (castTicks < CAST_TICKS) {
            return;
        }
        Zombie target = aimedAt;
        castTicks = NOT_CASTING;
        aimedAt = null;
        // Four seconds is long enough for the peasant to be shot, mown or knighted by another king. A
        // knighthood conferred on a corpse is not worth reporting, so the spell simply lapses.
        if (target == null || target.getHealth() == null || target.getHealth().isDead()
                || target.getHealth().hasArmor()) {
            return;
        }
        turnIntoKnight(target);
        king.getGameSession().reportEvent("The King Zombie knights a peasant zombie at ("
                + (int) target.getX() + ", " + target.getY() + "), granting it a crown and shoulder armor.");
    }

    private Zombie findSimpleZombieNearby(Zombie king) {
        if (king.getGameSession() == null || king.getGameSession().getMap() == null) {
            return null;
        }

        int kingRow = king.getMovement().getPositionY();
        double kingX = king.getMovement().getPositionX();

        for (int r = kingRow - KNIGHT_REACH_ROWS; r <= kingRow + KNIGHT_REACH_ROWS; r++) {
            if (r < 0 || r >= Constants.BOARD_ROWS) {
                continue;
            }
            Row row = king.getGameSession().getMap().getRow(r);
            if (row == null || row.getZombies() == null) {
                continue;
            }
            for (Zombie z : row.getZombies()) {
                // "Around it" is a reach, not a lane. Without the column test the king knighted a
                // peasant at the far end of the lawn, which is neither what the data says
                // (KnightingAreaX is 4) nor something a player could see happening.
                if (z != king && !z.getHealth().isDead() && !z.getState().isHypnotized()
                        && Math.abs(kingX - z.getMovement().getPositionX()) <= KNIGHT_REACH_COLS
                        && isSimpleZombie(z)) {
                    return z;
                }
            }
        }

        return null;
    }

    // A PEASANT: the plain zombie, wearing nothing.
    //
    // The alias test is the half that was missing, and "no armour" is not a substitute for it -- a Dodo
    // Rider and an Imp are both unarmoured, and knighting either produced a bird with a crown. The spec
    // says the king ennobles the SIMPLE zombies around it, and this roster has exactly one of those.
    private static final String PEASANT_ALIAS = "ZombieDefault";

    private boolean isSimpleZombie(Zombie zombie) {
        if (zombie.getHealth() == null) {
            return false;
        }
        return PEASANT_ALIAS.equalsIgnoreCase(zombie.getAlias()) && !zombie.getHealth().hasArmor();
    }

    private void turnIntoKnight(Zombie target) {
        if (target.getHealth() == null) return;
        target.getHealth().addLayer(new HealthLayer(ArmorType.CROWN));
        target.getHealth().addLayer(new HealthLayer(ArmorType.SHOULDER_ARMOR));
    }
}
