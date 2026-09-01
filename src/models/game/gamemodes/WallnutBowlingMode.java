package models.game.gamemodes;

import factories.ZombieFactory;
import models.entities.plants.bowling.BowlingKind;
import models.entities.plants.bowling.BowlingType;
import models.entities.plants.bowling.BowlingWallnut;
import models.entities.plants.bowling.ExplodeONut;
import models.entities.plants.bowling.GiantWallnut;
import models.entities.projectiles.Element;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Row;
import models.templates.ZombieTemplate;
import utils.Constants;
import utils.Result;
import utils.registry.ZombieRegistry;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Wall-nut Bowling mini-game. The player never picks a loadout and no sun falls: instead a conveyor
// belt hands out bowling nuts, which the player bowls from behind a red line. A nut rolls right toward
// the zombies; what it does on contact depends on its kind (see BowlingKind). The player wins by
// clearing every zombie and loses if one reaches the house (there are no lawn mowers here).
public class WallnutBowlingMode extends StandardMode {

    // The player may only bowl from columns left of this line (the "red line" on the lawn).
    private static final int RED_LINE_COLUMN = 3;
    private static final int CONVEYOR_INTERVAL_TICKS = 5 * Constants.TICKS_PER_SECOND;
    private static final int CONVEYOR_MAX = 6;
    // Zombies arrive in waves rather than one opening burst: BASE_WAVES waves plus one per difficulty
    // tier, each a little bigger than the last, with a breather in between. The level is only won once
    // every wave has come and gone.
    private static final int BASE_WAVES = 3;
    private static final int WAVES_PER_DIFFICULTY = 1;
    private static final int WAVE_INTERVAL_TICKS = 20 * Constants.TICKS_PER_SECOND;
    private static final int BASE_ZOMBIES_PER_ROW = 1;

    // ## How a wave actually walks on
    //
    // A wave used to be materialised in one tick as a RECTANGLE: every lane got the identical count, at
    // the identical integer columns (BOARD_COLS - 1 + i, so 8, 9, 10 ...), which meant the first zombie
    // of every row appeared already standing ON column 8 rather than walking in from off the edge, and
    // the rest queued behind it in five perfectly aligned columns. Forty zombies in a grid, stepping in
    // lockstep. Nothing about it read as a horde arriving, and it made the mode trivially readable: with
    // every lane identical there was never a reason to bowl at one lane rather than another.
    //
    // Three changes, and the wave's SIZE is deliberately not one of them -- the budget below is exactly
    // what the old per-row count summed to, so difficulty and pacing are untouched:
    //
    //   * The wave becomes a QUEUE that drains over time instead of a burst. Zombies walk on a few
    //     tenths of a second apart, which is what spaces them out along the lane.
    //   * The budget is dealt UNEVENLY across the lanes -- one each so no lane is safe to ignore, then
    //     the remainder scattered at random -- so a heavy lane is a thing the player has to notice.
    //   * Each one enters at ZOMBIE_SPAWN_X (the same half-cell-off-the-edge every other mode in the
    //     game uses), scattered BACKWARDS off it, so no two arrive on the same footing.
    //
    // ## The scatter runs the only direction it can
    //
    // Backwards -- toward the board -- and never past ZOMBIE_SPAWN_X, because CombatSystem.processDeaths
    // deletes any zombie whose x exceeds exactly that. The threshold is right and deliberate (it is what
    // stops a Prospector blown off the far edge from wandering into the desert and holding the level
    // open forever), but it means the spawn point is a CEILING and not merely a convention: a scatter
    // added to it rather than subtracted from it spawns zombies straight into the sweep, and the whole
    // wave vanishes on the tick it arrives, one "wanders off the far end of the lawn" line each.
    //
    // Half a cell, which is as much as there is room for: below 9.0 a zombie is ON the board (isOnBoard
    // is x < BOARD_COLS) and would pop into existence already standing on column 8, which is the exact
    // thing the old rectangle did wrong.
    private static final int SPAWN_GAP_TICKS = 8;
    private static final double ENTRY_SCATTER = 0.5;
    private static final double HIT_RADIUS = 0.5;        // how close a nut must be to strike a zombie
    private static final int CHERRY_BOMB_DAMAGE = 1800;  // Explode-o-Nut's 3x3 blast
    private static final double EXPLODE_COL_RADIUS = 1.5;
    private static final int EXPLODE_ROW_RADIUS = 1;
    private static final int FALLBACK_NORMAL_HP = 200;
    private static final String BASIC_ZOMBIE = "ZombieDefault";
    private static final String ARMORED_ZOMBIE = "ZombieArmor1";

    private final int difficulty;
    private final Random random;
    private final List<BowlingType> balls = new ArrayList<>();
    private final List<BowlingKind> conveyor = new ArrayList<>();
    private final Map<BowlingType, Zombie> lastHit = new IdentityHashMap<>();
    private long conveyorTimer;
    private long waveTimer;
    private int totalWaves;
    private int wavesReleased;
    private int nextBallId = 1;
    private int normalZombieHp = FALLBACK_NORMAL_HP;
    private int zombiesSpawned;
    private boolean started;

    public WallnutBowlingMode(int difficulty) {
        this(difficulty, new Random());
    }

    // Seeded variant so board setup and conveyor draws are reproducible in a test.
    public WallnutBowlingMode(int difficulty, Random random) {
        this.difficulty = Math.max(1, difficulty);
        this.random = random != null ? random : new Random();
    }

    // --- Mode contract ---------------------------------------------------------------------------

    @Override
    public void onStart(GameSession session) {
        if (started) {
            return;
        }
        started = true;
        normalZombieHp = resolveNormalHp();
        for (Row row : session.getMap().getRows()) {
            row.setLawnmower(null);   // no mowers in Wall-nut Bowling
        }
        // Seed the belt with one of each nut so the player has variety from the first move.
        conveyor.add(BowlingKind.BOWLING);
        conveyor.add(BowlingKind.EXPLODE);
        conveyor.add(BowlingKind.GIANT);
        session.reportEvent("Wall-nut Bowling! You pick no plants and no sun falls -- nuts arrive on "
                + "the conveyor instead. Roll one with \"bowl -t <bowling|explode|giant> -l (x, y)\""
                + " from behind the red line, columns 0-" + (RED_LINE_COLUMN - 1) + ".");
        totalWaves = BASE_WAVES + (difficulty - 1) * WAVES_PER_DIFFICULTY;
        releaseWave(session);   // wave 1 walks on immediately
    }

    // Drives the conveyor delivery, the wave clock and the rolling-nut physics each tick.
    @Override
    public void onTick(GameSession session) {
        conveyorTimer++;
        if (conveyorTimer >= CONVEYOR_INTERVAL_TICKS && conveyor.size() < CONVEYOR_MAX) {
            conveyor.add(randomKind());
            conveyorTimer = 0;
        }
        if (wavesReleased < totalWaves) {
            waveTimer++;
            if (waveTimer >= WAVE_INTERVAL_TICKS) {
                releaseWave(session);
                waveTimer = 0;
            }
        }
        drainSpawns(session);
        stepBalls(session);
    }

    // Won only once every wave has been released AND has finished walking on AND the lawn is clear.
    //
    // `pending.isEmpty()` is not optional now that a wave arrives over several seconds: without it the
    // last wave's first zombie could be bowled over before its second had left the queue, and the level
    // would declare victory with a dozen still to come.
    @Override
    public boolean checkWin(GameSession session) {
        return wavesReleased >= totalWaves && pending.isEmpty()
                && zombiesSpawned > 0 && livingZombies(session) == 0;
    }

    @Override
    public boolean checkLose(GameSession session) {
        for (Row row : session.getMap().getRows()) {
            for (Zombie zombie : row.getZombies()) {
                if (!zombie.getHealth().isDead() && zombie.getX() <= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean requiresSeedSelection(GameSession session) {
        return false;
    }

    @Override
    public boolean allowsSkySun() {
        return false;
    }

    // --- Player action: bowl a nut ---------------------------------------------------------------

    public Result bowlNut(GameSession session, String typeStr, int x, int y) {
        BowlingKind kind = parseKind(typeStr);
        if (kind == null) {
            return new Result(false, "No such nut as \"" + typeStr + "\". Try: bowling, explode, giant.");
        }
        if (x < 0 || x >= RED_LINE_COLUMN) {
            return new Result(false, "Stay behind the red line! Bowl from columns 0-"
                    + (RED_LINE_COLUMN - 1) + ".");
        }
        if (y < 0 || y >= session.getMap().getRows().size()) {
            return new Result(false, "There's no lane " + y + " on this lawn.");
        }
        if (!conveyor.remove(kind)) {
            return new Result(false, "No " + kind.name().toLowerCase()
                    + " nut on the belt right now. Check \"show map\" for what you've got.");
        }
        balls.add(create(kind, x, y));
        return new Result(true, "Thunk! The " + kind.name().toLowerCase()
                + " nut rolls away down lane " + y + ". Strike!");
    }

    // --- Rolling-nut physics ---------------------------------------------------------------------

    private void stepBalls(GameSession session) {
        int cols = Constants.BOARD_COLS;
        int rows = session.getMap().getRows().size();
        for (BowlingType ball : new ArrayList<>(balls)) {
            ball.advance();
            // Only the standard nut ricochets; the giant and explosive nuts travel dead straight.
            // Bouncing off the top/bottom wall reflects the heading -- a 90-degree turn for a nut
            // rolling at 45 degrees, per the doc -- and always sends it back onto the lawn.
            if (ball.getKind() == BowlingKind.BOWLING) {
                if (ball.getPy() < 0) {
                    ball.setPy(0);
                    ball.reflectVertical();
                } else if (ball.getPy() > rows - 1) {
                    ball.setPy(rows - 1);
                    ball.reflectVertical();
                }
            }
            Zombie hit = firstZombieHit(session, ball);
            if (hit != null) {
                resolveHit(session, ball, hit);
            } else {
                lastHit.remove(ball);                // no longer overlapping its previous target
            }
            if (ball.isFinished() || ball.getPx() > cols || ball.getPx() < -1) {
                balls.remove(ball);
                lastHit.remove(ball);
            }
        }
    }

    // The nearest live zombie the nut is overlapping in its row, skipping the one it just struck (so a
    // single pass-through counts as one hit, not one per tick while overlapping).
    private Zombie firstZombieHit(GameSession session, BowlingType ball) {
        int row = ball.getRow();
        if (row < 0 || row >= session.getMap().getRows().size()) {
            return null;
        }
        Zombie previous = lastHit.get(ball);
        Zombie best = null;
        double bestDx = Double.MAX_VALUE;
        boolean previousStillOverlapping = false;
        for (Zombie z : session.getMap().getRow(row).getZombies()) {
            if (z.getHealth().isDead()) {
                continue;
            }
            double dx = Math.abs(z.getX() - ball.getPx());
            if (dx > HIT_RADIUS) {
                continue;
            }
            if (z == previous) {
                previousStillOverlapping = true;
                continue;
            }
            if (dx < bestDx) {
                bestDx = dx;
                best = z;
            }
        }
        if (best == null && !previousStillOverlapping) {
            lastHit.remove(ball);
        }
        return best;
    }

    private void resolveHit(GameSession session, BowlingType ball, Zombie zombie) {
        switch (ball.getKind()) {
            case BOWLING:
                zombie.getHealth().applyDamage(normalZombieHp, Element.NEUTRAL, null);
                ball.deflect();                      // 45 degrees, still forward -- see BowlingType
                lastHit.put(ball, zombie);
                break;
            case EXPLODE:
                explode(session, ball);
                ball.finish();
                break;
            case GIANT:
                zombie.getHealth().applyDamage(zombie.getHealth().getTotalHP(), Element.NEUTRAL, null);
                lastHit.put(ball, zombie);           // crushed it, but keep rolling straight
                break;
            default:
                break;
        }
    }

    // Cherry-Bomb-style 3x3 blast centred on the nut.
    private void explode(GameSession session, BowlingType ball) {
        int centreRow = ball.getRow();
        for (int r = centreRow - EXPLODE_ROW_RADIUS; r <= centreRow + EXPLODE_ROW_RADIUS; r++) {
            if (r < 0 || r >= session.getMap().getRows().size()) {
                continue;
            }
            for (Zombie z : session.getMap().getRow(r).getZombies()) {
                if (!z.getHealth().isDead() && Math.abs(z.getX() - ball.getPx()) <= EXPLODE_COL_RADIUS) {
                    z.getHealth().applyDamage(CHERRY_BOMB_DAMAGE, Element.NEUTRAL, null);
                }
            }
        }
    }

    // --- Setup helpers ---------------------------------------------------------------------------

    // Queues one wave. Each wave is bigger than the last (and difficulty adds on top), so the pressure
    // builds instead of peaking on the opening burst. Reports the wave so the player knows how many are
    // still to come -- and how many are coming, which is the number this queues rather than the number
    // standing on the lawn a tick later.
    private void releaseWave(GameSession session) {
        wavesReleased++;
        int rows = session.getMap().getRows().size();
        int budget = rows * (BASE_ZOMBIES_PER_ROW + difficulty + (wavesReleased - 1));
        // One per lane first: an empty lane is a lane the player never has to look at, and five of them
        // scattered at random would happen often enough to matter.
        for (int y = 0; y < rows; y++) {
            pending.add(y);
        }
        for (int i = rows; i < budget; i++) {
            pending.add(random.nextInt(rows));
        }
        // Shuffled so the guaranteed one-per-lane opening is not five zombies walking on in lane order,
        // which would be the old rectangle again in slow motion.
        java.util.Collections.shuffle(pending, random);
        session.reportEvent("Wave " + wavesReleased + " of " + totalWaves + " shambles in -- "
                + budget + " zombies. Let 'em roll!");
    }

    // Lanes waiting to have a zombie walk into them, one per zombie still to arrive.
    private final List<Integer> pending = new ArrayList<>();
    private int spawnTimer;

    // Lets one queued zombie walk on, every SPAWN_GAP_TICKS.
    //
    // The gap is what does most of the spacing, and it does far more of it than its own length suggests:
    // successive spawns go to different lanes, so two zombies sharing a lane are typically five spawns
    // -- most of four seconds -- apart, which at a walker's pace is a good cell of clear ground between
    // them. The scatter then breaks the last of the regularity.
    private void drainSpawns(GameSession session) {
        if (pending.isEmpty()) {
            return;
        }
        if (++spawnTimer < SPAWN_GAP_TICKS) {
            return;
        }
        spawnTimer = 0;
        int lane = pending.remove(pending.size() - 1);
        double x = Constants.ZOMBIE_SPAWN_X - random.nextDouble() * ENTRY_SCATTER;
        Zombie zombie = ZombieFactory.createZombie(randomZombieAlias(), x, lane, session);
        if (zombie != null) {
            session.getMap().getRow(lane).getZombies().add(zombie);
            zombiesSpawned++;
        }
    }

    private BowlingType create(BowlingKind kind, int x, int y) {
        double px = x + 0.5;
        switch (kind) {
            case EXPLODE:
                return new ExplodeONut("Explode-o-Nut", nextBallId++, px, y);
            case GIANT:
                return new GiantWallnut("Giant Wall-nut", nextBallId++, px, y);
            case BOWLING:
            default:
                return new BowlingWallnut("Bowling Wall-nut", nextBallId++, px, y);
        }
    }

    private BowlingKind randomKind() {
        double r = random.nextDouble();
        if (r < 0.15) {
            return BowlingKind.GIANT;
        }
        if (r < 0.35) {
            return BowlingKind.EXPLODE;
        }
        return BowlingKind.BOWLING;
    }

    private String randomZombieAlias() {
        if (difficulty >= 2 && random.nextDouble() < 0.35) {
            return ARMORED_ZOMBIE;
        }
        return BASIC_ZOMBIE;
    }

    private int resolveNormalHp() {
        ZombieTemplate template = ZombieRegistry.getInstance().getZombieTemplateByAlias(BASIC_ZOMBIE);
        return template != null && template.getBaseHp() > 0 ? template.getBaseHp() : FALLBACK_NORMAL_HP;
    }

    private BowlingKind parseKind(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw.toLowerCase().trim()) {
            case "bowling":
            case "bowlingwallnut":
            case "wallnut":
            case "nut":
                return BowlingKind.BOWLING;
            case "explode":
            case "explosive":
            case "explodeonut":
                return BowlingKind.EXPLODE;
            case "giant":
            case "giantwallnut":
                return BowlingKind.GIANT;
            default:
                return null;
        }
    }

    // --- Inspection (map view / verification harness) --------------------------------------------

    public List<BowlingType> getBalls() {
        return new ArrayList<>(balls);
    }

    public List<BowlingKind> getConveyor() {
        return new ArrayList<>(conveyor);
    }

    // How many of each nut are waiting on the belt, in a stable order and including the kinds the player
    // currently has none of -- this is what the map view reports so the player always knows what they
    // can bowl right now.
    public Map<BowlingKind, Integer> conveyorCounts() {
        Map<BowlingKind, Integer> counts = new java.util.LinkedHashMap<>();
        for (BowlingKind kind : BowlingKind.values()) {
            counts.put(kind, 0);
        }
        for (BowlingKind kind : conveyor) {
            counts.merge(kind, 1, Integer::sum);
        }
        return counts;
    }

    public int conveyorSize() {
        return conveyor.size();
    }

    public int conveyorCapacity() {
        return CONVEYOR_MAX;
    }

    public int getWavesReleased() {
        return wavesReleased;
    }

    public int getTotalWaves() {
        return totalWaves;
    }

    public int getZombiesSpawned() {
        return zombiesSpawned;
    }

    public int getRedLineColumn() {
        return RED_LINE_COLUMN;
    }

    public int getNormalZombieHp() {
        return normalZombieHp;
    }

    public int getDifficulty() {
        return difficulty;
    }
}
