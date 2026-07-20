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
    private static final int CONVEYOR_INTERVAL_TICKS = 8 * Constants.TICKS_PER_SECOND;
    private static final int CONVEYOR_MAX = 6;
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
        spawnZombies(session);
    }

    // Drives the conveyor delivery and the rolling-nut physics each tick.
    @Override
    public void onTick(GameSession session) {
        conveyorTimer++;
        if (conveyorTimer >= CONVEYOR_INTERVAL_TICKS && conveyor.size() < CONVEYOR_MAX) {
            conveyor.add(randomKind());
            conveyorTimer = 0;
        }
        stepBalls(session);
    }

    @Override
    public boolean checkWin(GameSession session) {
        return zombiesSpawned > 0 && livingZombies(session) == 0;
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
            return new Result(false, "Unknown nut \"" + typeStr + "\". Use: bowling, explode, or giant.");
        }
        if (x < 0 || x >= RED_LINE_COLUMN) {
            return new Result(false, "You can only bowl from columns 0-" + (RED_LINE_COLUMN - 1)
                    + " (behind the red line at column " + RED_LINE_COLUMN + ").");
        }
        if (y < 0 || y >= session.getMap().getRows().size()) {
            return new Result(false, "Row " + y + " is off the board.");
        }
        if (!conveyor.remove(kind)) {
            return new Result(false, "There is no " + kind.name().toLowerCase()
                    + " nut on the conveyor right now.");
        }
        balls.add(create(kind, x, y));
        return new Result(true, "You bowled a " + kind.name().toLowerCase()
                + " nut down row " + y + " from column " + x + "!");
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
                ball.rotate(45);                     // 45-degree turn after striking a zombie
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

    private void spawnZombies(GameSession session) {
        int rows = session.getMap().getRows().size();
        int perRow = 1 + difficulty;                 // more zombies at higher difficulty
        for (int y = 0; y < rows; y++) {
            for (int i = 0; i < perRow; i++) {
                double x = Constants.BOARD_COLS - 1 - i;   // line them up entering from the right
                Zombie zombie = ZombieFactory.createZombie(randomZombieAlias(), x, y, session);
                if (zombie != null) {
                    session.getMap().getRow(y).getZombies().add(zombie);
                    zombiesSpawned++;
                }
            }
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
