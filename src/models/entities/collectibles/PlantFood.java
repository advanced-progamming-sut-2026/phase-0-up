package models.entities.collectibles;

import models.game.GameSession;
import utils.Constants;

// The plant food a glowing zombie leaves on the lawn when it dies.
//
// It used to be paid straight into the counter the moment the zombie fell -- correct arithmetic, and
// nothing to see or do. This is the pickup: it lies where the zombie died, glows, and is claimed by
// clicking it, or it goes stale and is gone.
//
// ## Why it is its own class rather than a Sun with a different payout
//
// Nothing about a sun's behaviour fits. A sun falls from the sky over five seconds, is worth a
// variable amount that decides how big it is drawn, comes in three types, and one of those types
// explodes if caught in mid-air. A plant food does none of that: it is placed on the ground, always
// worth exactly one, and always the same object. Sharing the class would mean a Sun with a type that
// ignores half its own fields, and SunSystem.collectSun would start having to ask what it was holding.
//
// What IS shared is Collectible, which is where the parts that genuinely are the same live: the
// collected/expired pair, the expiry countdown, and the rule that an effect applies exactly once.
public class PlantFood extends Collectible {

    // Long enough to notice a drop at the far end of the lawn and cross to it, short enough that
    // ignoring one is a real loss. The view starts flashing it three seconds before this runs out.
    public static final int EXPIRE_TICKS = 12 * Constants.TICKS_PER_SECOND;

    public PlantFood(double x, int y) {
        this(x, y, EXPIRE_TICKS);
    }

    public PlantFood(double x, int y, int expireTicks) {
        super("plant-food", onBoardX(x), onBoardRow(y), expireTicks);
    }

    @Override
    protected void applyEffect(GameSession gameSession) {
        gameSession.increasePlantFoodCount(1);
    }

    // Told its remaining life rather than counting it down. Same reason as Sun.placeAt: on a networked
    // client the board is mirrored from the server's snapshot and nothing on it ticks, so a mirrored
    // pickup that counted for itself would never move off its starting number -- and the view reads
    // this to decide when to start flashing. The mirror is removed when it stops appearing in
    // snapshots, so it must never expire locally either.
    public void mirrorRemainingTicks(int ticks) {
        this.expireTicks = Math.max(0, ticks);
    }

    // The tile this is COLLECTED by naming, and -- exactly as with Sun.tileColumn -- the only place
    // that rule is written down.
    //
    // "collect plant-food -l (x, y)" addresses a tile, so the view has to name the same one the model
    // will look in. Sun learned this the hard way: the rule lived in two layers, the two copies read
    // different fields, and the networked build ended up with collectibles a player could see, could
    // click, and could not pick up. One method, asked by both sides.
    public int tileColumn() {
        return (int) Math.floor(x);
    }

    public int tileRow() {
        return y;
    }

    // A pickup that comes to rest off the board cannot be collected, so one is never built off the
    // board. A zombie dies at a continuous x that runs from past the house (negative) to off the far
    // edge (9.5 at spawn), and either end names a tile the collect command refuses outright -- so the
    // drop would lie there, drawn and clickable, answering every click with "invalid coordinates".
    //
    // The same rule Sun applies, for the same reason and after the same bug. See Sun.onBoardX.
    private static double onBoardX(double x) {
        // Just inside the last column, so Math.floor still names a column that exists.
        return Math.max(0d, Math.min(x, Constants.BOARD_COLS - 0.001d));
    }

    private static int onBoardRow(int y) {
        return Math.max(0, Math.min(y, Constants.BOARD_ROWS - 1));
    }
}
