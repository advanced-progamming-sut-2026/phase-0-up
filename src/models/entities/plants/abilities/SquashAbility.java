package models.entities.plants.abilities;

import models.entities.plants.Plant;
import models.entities.plants.abilities.triggers.TriggerStrategy;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import utils.Constants;

import java.util.List;

// The Squash jumps on a zombie. It does not explode.
//
// It used to be a DelayedExplosiveAbility with a 0x0 blast radius, which is arithmetically the same
// thing -- one tile, everything on it dies -- and visually nothing like it: the view draws an
// explosion for any plant that announces a detonation, so a Squash went up in a Cherry Bomb fireball,
// and the eight jump clips its own art ships (jump_up_left/right, jump_down_left/right, and the two
// plant-food variants) were never played by anything.
//
// ## The leap is a real interval, not an instant
//
// A plant that acts and dies on the same tick gives a renderer no window at all -- that is why the
// jump had nowhere to live. So the leap is a small state machine: it commits to a target, spends
// LEAP_TICKS in the air with isWindingUp() true (the same signal every shooter's attack animation
// hangs off), and lands on the tick that expires. The damage happens on the LANDING, which is also
// what makes it fair: a zombie that walks out from under a squash in flight is genuinely missed.
public class SquashAbility extends PlantAbility {

    // Long enough to read as a jump, short enough that a zombie cannot stroll past underneath. The
    // art's jump_up and jump_down are 0.8s each; this covers the up half, and the view plays the down
    // half over the landing.
    private static final int LEAP_TICKS = 10;

    // How far ahead a squash will commit to. Its trigger already only fires on contact, so this is
    // the reach of the LEAP itself once triggered -- a squash lands on the tile in front of it.
    private static final double LEAP_REACH = 1.5;

    private final int damage;
    private final Element element;

    // -1 = standing. Counts down while it is in the air.
    private int leapRemaining = -1;

    // Where this leap is going, decided when it commits rather than when it lands: a squash that
    // re-aimed mid-air at whatever had come closest would never miss, and the view would have to
    // redraw its arc every frame.
    private double landingX;
    private int landingRow;
    private boolean facingRight = true;

    // Extra leaps queued by plant food, and whether the CURRENT one is one of them -- the art has its
    // own descent for a boosted squash, so the view has to be able to tell.
    private int extraLeaps;
    private boolean boostedLeap;

    public SquashAbility(int actionIntervalTicks, TriggerStrategy triggerStrategy, int damage,
                         Element element) {
        super(actionIntervalTicks, triggerStrategy);
        this.damage = damage;
        this.element = element;
    }

    // ---- what the view reads ---------------------------------------------------------------------

    // In the air. The view plays the jump clips across exactly this window.
    @Override
    public boolean isWindingUp() {
        return leapRemaining >= 0;
    }

    // Which way it turned to face its target, so the view picks jump_*_right or jump_*_left rather
    // than guessing from the plant's own (always forward-facing) orientation.
    public boolean isFacingRight() {
        return facingRight;
    }

    // Whether this leap is a plant-food one, which the art draws differently.
    public boolean isBoostedLeap() {
        return boostedLeap;
    }

    // Where this leap is going. The view carries the squash there along an arc, which is the whole of
    // "it jumps ON the zombie" -- played on its own tile the animation is a one-second wobble in
    // place, and a player watching the lane does not see a jump at all.
    public int landingColumn() {
        return Math.max(0, Math.min((int) landingX, Constants.BOARD_COLS - 1));
    }

    public int landingRow() {
        return landingRow;
    }

    // How far through the leap it is, 0 at launch and 1 at the landing. The view splits its jump
    // animation on this rather than on a clock of its own, so the squash cannot be drawn still rising
    // while the model has already crushed something.
    // How much leapProgress() advances per model tick. The view needs it to interpolate BETWEEN ticks:
    // the model runs at 10 Hz and the screen at 60, so a position taken straight from leapProgress()
    // moves in ten visible steps and the jump stutters.
    public float leapStep() {
        return 1f / LEAP_TICKS;
    }

    public float leapProgress() {
        if (leapRemaining < 0) {
            return 0f;
        }
        return 1f - (leapRemaining / (float) LEAP_TICKS);
    }

    @Override
    public boolean isPlantFoodBusy() {
        return extraLeaps > 0 || (boostedLeap && leapRemaining >= 0);
    }

    // ---- plant food ------------------------------------------------------------------------------

    // Plant food: the squash goes again, and again. Each extra leap picks its own target, so a fed
    // squash clears a small crowd instead of the one zombie that woke it.
    //
    // This replaced a DESTROY_RANDOM strategy that deleted two zombies anywhere on the board -- no
    // jump, no travel, nothing on screen, and a squash in row 0 killing something in row 4.
    public void queueExtraLeaps(int leaps) {
        this.extraLeaps += Math.max(0, leaps);
        this.boostPatience = BOOST_PATIENCE_TICKS;
    }

    // How long a fed squash will wait for something to jump on before the rest of the boost lapses.
    //
    // Without a limit a squash fed over an empty lane keeps its queued leaps for ever: it never spends
    // them, isPlantFoodBusy() never goes false, and it sits there glowing for the rest of the level
    // with a boost that has no way to resolve. Five seconds is long enough for the next zombie in a
    // wave to walk into range and short enough that a mistimed feed visibly ends.
    private static final int BOOST_PATIENCE_TICKS = 5 * Constants.TICKS_PER_SECOND;

    private int boostPatience;

    // ---- the leap --------------------------------------------------------------------------------

    @Override
    public boolean canExecute(Plant owner, GameSession gameSession) {
        if (leapRemaining >= 0) {
            return false;   // already committed to one
        }
        return super.canExecute(owner, gameSession);
    }

    @Override
    public void execute(Plant owner, GameSession gameSession) {
        beginLeap(owner, gameSession, false);
    }

    @Override
    public void update(Plant owner, GameSession gameSession) {
        if (leapRemaining > 0) {
            leapRemaining--;
            return;   // in the air: nothing else this tick, and no new trigger either
        }
        if (leapRemaining == 0) {
            leapRemaining = -1;
            land(owner, gameSession);
            return;
        }
        // Back on the ground with plant food still owed: go again, at the next zombie it can find.
        //
        // The leap is spent only if it actually LAUNCHES. Decrementing first threw the plant food away:
        // beginLeap answers "nothing in reach" by doing nothing, so feeding a squash with no zombie
        // next to it -- which is when a player feeds one, right after planting it or as the wave comes
        // in -- burned both queued leaps in two ticks and looked like the plant food did nothing at all.
        if (extraLeaps > 0 && !owner.isDead()) {
            if (beginLeap(owner, gameSession, true)) {
                extraLeaps--;
                boostPatience = BOOST_PATIENCE_TICKS;   // refreshed between hops
                return;
            }
            // Nothing in the lane to jump on. Waits, then gives up -- see BOOST_PATIENCE_TICKS.
            if (--boostPatience <= 0) {
                extraLeaps = 0;
                boostedLeap = false;
                gameSession.reportEvent(owner.getName()
                        + " runs out of things to squash and settles back down.");
            }
        }
        super.update(owner, gameSession);
    }

    // Commits to a target and leaves the ground. False when there is nothing to land on -- the squash
    // simply stays put, which is what makes it a trap rather than a timer.
    private boolean beginLeap(Plant owner, GameSession gameSession, boolean boosted) {
        Zombie target = nearestTarget(owner, gameSession, boosted);
        if (target == null) {
            return false;
        }
        this.boostedLeap = boosted;
        this.landingX = target.getMovement().getPositionX();
        this.landingRow = target.getMovement().getPositionY();
        this.facingRight = landingX >= owner.getX();
        this.leapRemaining = LEAP_TICKS;

        gameSession.reportEvent(owner.getName() + " leaps at the " + target.getAlias()
                + " in lane " + landingRow + "!");
        return true;
    }

    // Comes down. Everything standing on the landing tile is crushed; the squash is spent unless plant
    // food has bought it another go.
    private void land(Plant owner, GameSession gameSession) {
        int crushed = crushLandingTile(gameSession);

        gameSession.reportEvent(owner.getName() + " lands with a SPLAT at ("
                + (int) landingX + ", " + landingRow + ")"
                + (crushed == 0 ? " -- and squashes nothing but grass." : "!"));

        // A squash is one-use. Consumed AFTER the landing so the damage is dealt by a live plant, and
        // only once plant food has no leaps left to spend.
        if (extraLeaps <= 0 && owner.getHealth() != null) {
            owner.getHealth().takeDamage(owner.getHealth().getMaxHp());
        }
    }

    // Everything on the tile it came down on, which is what "it lands ON the zombie" has to mean: a
    // squash flattens the one it jumped at and anything that happened to be standing there too.
    private int crushLandingTile(GameSession gameSession) {
        if (landingRow < 0 || landingRow >= Constants.BOARD_ROWS) {
            return 0;
        }
        List<Zombie> zombies = gameSession.getMap().getRow(landingRow).getZombies();
        if (zombies == null) {
            return 0;
        }
        int crushed = 0;
        // Copied, because applying damage can kill a zombie and a death handler may touch the row.
        for (Zombie zombie : new java.util.ArrayList<>(zombies)) {
            if (!zombie.isTargetable()) {
                continue;
            }
            if (Math.abs(zombie.getMovement().getPositionX() - landingX) <= 0.5) {
                zombie.getHealth().applyDamage(damage, element, null);
                crushed++;
            }
        }
        return crushed;
    }

    // What this leap is going to land on.
    //
    // An ORDINARY leap is a trap springing: it takes whatever has walked into the tile in front of it,
    // in its own lane only, and takes nothing if the lane is empty.
    //
    // A BOOSTED leap hunts the whole board, nearest first. That is what the plant food buys, and
    // restricting it to the squash's own row is what made the boost do nothing in a real game: the
    // squash has just flattened the one zombie that woke it, the rest of the wave is spread across
    // the other four lanes, and so the second and third leaps found no target at all. The strategy
    // this replaced (DESTROY_RANDOM) killed two zombies anywhere on the board, and "anywhere on the
    // board" is the part that has to survive -- what changes is that the squash now visibly goes there.
    private Zombie nearestTarget(Plant owner, GameSession gameSession, boolean boosted) {
        if (!boosted) {
            return nearestInLane(owner, gameSession.getMap().getRow(owner.getY()).getZombies(),
                    LEAP_REACH);
        }
        Zombie best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int row = 0; row < Constants.BOARD_ROWS; row++) {
            Zombie candidate = nearestInLane(owner,
                    gameSession.getMap().getRow(row).getZombies(), Double.MAX_VALUE);
            if (candidate == null) {
                continue;
            }
            // Rows count for as much as columns, so a squash prefers the zombie beside it to one four
            // lanes away -- otherwise a fed squash spends its leaps criss-crossing the board.
            double dx = candidate.getMovement().getPositionX() - owner.getX();
            double dy = row - owner.getY();
            double distance = (dx * dx) + (dy * dy);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private Zombie nearestInLane(Plant owner, List<Zombie> zombies, double reach) {
        if (zombies == null) {
            return null;
        }
        Zombie best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Zombie zombie : zombies) {
            if (!zombie.isTargetable()) {
                continue;
            }
            double distance = Math.abs(zombie.getMovement().getPositionX() - owner.getX());
            if (distance <= reach && distance < bestDistance) {
                best = zombie;
                bestDistance = distance;
            }
        }
        return best;
    }
}
