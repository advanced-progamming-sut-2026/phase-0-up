package models.game.gamemodes;

import factories.ZombieFactory;
import models.entities.zombies.BossAttack;
import models.entities.zombies.BossKind;
import models.entities.zombies.Zomboss;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Row;
import utils.Constants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// The season finale: one Zomboss, and the level is over when it falls.
//
// ## What the mode owns
//
// The fight's CLOCK, and nothing else. Four timers run side by side -- deliver a plant, shift a row,
// summon a minion, throw an attack -- and each is gated on the machine being upright. The attacks
// themselves live in ZombossAttacks and the two-row bookkeeping lives on Zomboss; this is the part
// that decides when any of it happens.
//
// ## No seed selection, and a belt instead
//
// The spec's rule, and it is the reason a boss level plays differently from the four before it: the
// player never chooses a loadout, so they cannot answer the boss with a prepared counter. Plants
// arrive on a conveyor and are spent as they come, which is exactly the inventory GameMode already
// models for Vasebreaker (managesPlantInventory / hasPlantAvailable / consumePlant) -- so the whole
// belt is a List<String> here and GameSession.plant needed no change at all.
//
// The belt is a LIST rather than a bag of counts because the view draws it as one: the cards ride up
// a moving belt in delivery order, and a set of counts has no order to ride in. plantInventory() still
// answers with counts, because that is what the terminal's "show plant status" reads.
//
// ## Health bands and the dizzy window
//
// The boss's HP is split in three (see Zomboss). Every time the total falls through a band boundary
// the machine reels for a few seconds: no attacks, no summons, no shifting. That window is the whole
// shape of the fight -- it is when the player stops rebuilding and starts pushing damage -- and it is
// why the bar at the top is drawn in three segments rather than as one long drain.
public class ZombossMode extends StandardMode {

    // Under two Gargantuars' worth of machine, 2,000 to a band.
    //
    // Tuned against a measured fight rather than picked. The first number was 15,000, and playing it
    // through showed why that was wrong: the belt hands out one plant every couple of seconds and the
    // boss wipes both of its rows with a single move, so the player never holds more than a handful of
    // shooters and only some of their fire ever reaches the machine. At 15,000 a full two minutes of
    // play took it down 180 HP. Nine thousand, against the cadence below, is a fight of a few minutes.
    private static final int BOSS_BASE_HP = 6000;

    // The columns it parks in and never leaves ("stays in its columns"). Two cells wide at 7.5, so it
    // fills the back of the lawn without standing on the house.
    private static final double BOSS_COLUMN = 7.5;

    // Minions walk on from the far edge, under the machine that sent them.
    private static final double MINION_SPAWN_X = 8.5;

    private static final int SECONDS = Constants.TICKS_PER_SECOND;
    // Time to get a defence down before the machine starts working. Without it the first attack can
    // land on an empty lawn before the belt has delivered a third plant.
    private static final int OPENING_GRACE_TICKS = 12 * SECONDS;
    private static final int DIZZY_TICKS = 6 * SECONDS;
    // "Execute every few seconds randomly": a floor plus a random top-up, re-rolled after every move,
    // so the player cannot set a metronome by it.
    //
    // Eleven to seventeen seconds, not the six to ten it started at, and the belt below was sped up to
    // match. Both numbers come from watching the board: a single move wipes every plant in two of the
    // five rows, so at the old cadence the boss destroyed defences faster than the conveyor could
    // deliver them and the plant count sat at zero to two for the whole fight. A row now gets roughly
    // half a minute to be rebuilt before its turn comes round again.
    private static final int ATTACK_MIN_TICKS = 14 * SECONDS;
    private static final int ATTACK_JITTER_TICKS = 8 * SECONDS;
    private static final int SHIFT_INTERVAL_TICKS = 10 * SECONDS;
    private static final int SUMMON_INTERVAL_TICKS = 7 * SECONDS;
    private static final int CONVEYOR_INTERVAL_TICKS = 2 * SECONDS;
    private static final int CONVEYOR_MAX = 8;
    private static final int CONVEYOR_OPENING = 3;

    // What the belt hands out on a level whose file forgot to list anything, so the mode is playable
    // even against a malformed template rather than delivering nothing forever.
    private static final List<String> FALLBACK_PLANTS =
            List.of("Peashooter", "Sunflower", "Wall-nut", "Repeater", "Potato Mine");
    private static final String FALLBACK_MINION = "ZombieDefault";

    private final BossKind kind;
    private final List<String> minionPool;
    private final Random random;

    private final List<String> conveyor = new ArrayList<>();
    private List<String> plantPool = new ArrayList<>();

    private Zomboss boss;
    private boolean started;

    private int conveyorTimer;
    private int attackTimer;
    private int attackDue;
    private int shiftTimer;
    private int summonTimer;

    public ZombossMode(BossKind kind, List<String> minionPool) {
        this(kind, minionPool, new Random());
    }

    // Seeded variant so a test can pin which attacks come out in which order.
    public ZombossMode(BossKind kind, List<String> minionPool, Random random) {
        this.kind = kind != null ? kind : BossKind.SPHINX;
        this.minionPool = minionPool == null || minionPool.isEmpty()
                ? List.of(FALLBACK_MINION) : List.copyOf(minionPool);
        this.random = random != null ? random : new Random();
        this.attackDue = ATTACK_MIN_TICKS;
    }

    // --- Mode contract ---------------------------------------------------------------------------

    @Override
    public void onStart(GameSession session) {
        if (started) {
            return;
        }
        started = true;
        plantPool = resolvePlantPool(session);
        for (int i = 0; i < CONVEYOR_OPENING; i++) {
            conveyor.add(drawPlant());
        }
        spawnBoss(session);
        // Opens with the mode's own name, which is the convention every other banner keeps -- and it is
        // load-bearing: NpcLines matches banners by that opening token, so this is what puts the
        // sentence in Zomboss's own mouth instead of leaving four boss names to be listed there.
        session.reportEvent("Zomboss! The " + kind.getDisplayName() + " rises at the back of the "
                + "lawn. No seed picking here -- plants come up the conveyor, so spend them as they "
                + "arrive. Knock out all three of its armour bands to bring it down.");
    }

    @Override
    public void onTick(GameSession session) {
        runConveyor();
        if (boss == null || boss.getHealth().isDead()) {
            return;
        }
        if (boss.crossedSectionBoundary()) {
            boss.getState().applyDizzy(DIZZY_TICKS);
            session.reportEvent("The " + kind.getDisplayName() + " reels, sparking and dizzy -- "
                    + boss.sectionsRemaining() + " band(s) of armour left. Pour it on!");
        }
        if (boss.getState().isDizzy() || session.getTimeTicks() < OPENING_GRACE_TICKS) {
            return;
        }
        runAttacks(session);
        runShifting(session);
        runSummoning(session);
    }

    // Won the moment the machine falls. Deliberately NOT also "and the lawn is clear": the minions it
    // left behind are its leftovers, and making the player mop them up after the boss is on the floor
    // is an anticlimax rather than a challenge.
    @Override
    public boolean checkWin(GameSession session) {
        return started && boss != null && boss.getHealth().isDead();
    }

    @Override
    public boolean requiresSeedSelection(GameSession session) {
        return false;
    }

    @Override
    public String describeObjective(GameSession session) {
        return "Bring down the " + kind.getDisplayName() + ".\nKnock out all " + Zomboss.SECTIONS
                + " bands of its armour -- each one leaves it reeling.";
    }

    // Plants are free off the belt, so there is no economy for sun to feed. Same answer, and the same
    // reason, as Wall-nut Bowling and Vasebreaker.
    @Override
    public boolean allowsSkySun() {
        return false;
    }

    // --- The conveyor ----------------------------------------------------------------------------

    @Override
    public boolean managesPlantInventory() {
        return true;
    }

    @Override
    public boolean hasPlantAvailable(String plantType) {
        return indexOf(plantType) >= 0;
    }

    // Takes the FIRST matching card off the belt, which is the one nearest the top and therefore the
    // one the player was looking at when they clicked.
    @Override
    public void consumePlant(String plantType) {
        int index = indexOf(plantType);
        if (index >= 0) {
            conveyor.remove(index);
        }
    }

    @Override
    public String plantUnavailableMessage(String plantType) {
        return "No \"" + plantType + "\" on the belt yet -- plant what the conveyor brings you.";
    }

    @Override
    public Map<String, Integer> plantInventory() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String plant : conveyor) {
            counts.merge(plant, 1, Integer::sum);
        }
        return counts;
    }

    // The belt in delivery order, oldest first -- what the HUD rides its cards along.
    public List<String> getConveyor() {
        return new ArrayList<>(conveyor);
    }

    public int conveyorCapacity() {
        return CONVEYOR_MAX;
    }

    // The quiet at the start of the fight, before the machine begins working. Exposed the way
    // VersusIZombieMode exposes its own grace: a test that did not know about it would tick past the
    // first attack window, see nothing happen, and report a broken scheduler.
    public static int openingGraceTicks() {
        return OPENING_GRACE_TICKS;
    }

    public static int shiftIntervalTicks() {
        return SHIFT_INTERVAL_TICKS;
    }

    // --- Inspection (HUD, map view, tests) -------------------------------------------------------

    public Zomboss getBoss() {
        return boss;
    }

    public BossKind getKind() {
        return kind;
    }

    // --- The fight's four clocks ------------------------------------------------------------------

    private void runConveyor() {
        if (conveyor.size() >= CONVEYOR_MAX) {
            return;
        }
        if (++conveyorTimer >= CONVEYOR_INTERVAL_TICKS) {
            conveyorTimer = 0;
            conveyor.add(drawPlant());
        }
    }

    private void runAttacks(GameSession session) {
        if (++attackTimer < attackDue) {
            return;
        }
        attackTimer = 0;
        attackDue = ATTACK_MIN_TICKS + random.nextInt(ATTACK_JITTER_TICKS + 1);
        List<BossAttack> moves = kind.getAttacks();
        if (!moves.isEmpty()) {
            ZombossAttacks.perform(session, boss, moves.get(random.nextInt(moves.size())), random);
        }
    }

    // "Periodically shifts between rows". The Mammoth is the spec's exception and stands still.
    private void runShifting(GameSession session) {
        if (!kind.shiftsRows() || ++shiftTimer < SHIFT_INTERVAL_TICKS) {
            return;
        }
        shiftTimer = 0;
        int highest = session.getMap().getRows().size() - boss.rowSpan();
        if (highest <= 0) {
            return;
        }
        int current = boss.getMovement().getPositionY();
        int target = random.nextInt(highest + 1);
        if (target == current) {
            target = (current + 1) % (highest + 1);
        }
        fileBossInto(session, target);
        session.reportEvent("The " + kind.getDisplayName() + " heaves itself across to rows "
                + target + " and " + (target + 1) + ".");
    }

    // "Spawns a variety of random zombies". The Mammoth summons nothing, per the spec.
    private void runSummoning(GameSession session) {
        if (!kind.spawnsZombies() || ++summonTimer < SUMMON_INTERVAL_TICKS) {
            return;
        }
        summonTimer = 0;
        int lane = random.nextInt(session.getMap().getRows().size());
        String alias = minionPool.get(random.nextInt(minionPool.size()));
        Zombie minion = ZombieFactory.createZombie(alias, MINION_SPAWN_X, lane, session);
        if (minion == null) {
            return;
        }
        session.getMap().getRow(lane).getZombies().add(minion);
        session.reportEvent("The " + kind.getDisplayName() + " opens a portal in lane " + lane
                + " and a " + alias + " steps through.");
    }

    // --- Setup helpers ----------------------------------------------------------------------------

    private void spawnBoss(GameSession session) {
        int rows = session.getMap().getRows().size();
        int top = Math.max(0, (rows - 2) / 2);
        boss = ZombieFactory.createBoss(kind, BOSS_BASE_HP, BOSS_COLUMN, top, session);
        if (boss != null) {
            fileBossInto(session, top);
        }
    }

    // Puts the boss in both of its rows' zombie lists, and only those two.
    //
    // Row membership is how the rest of the game answers "what can this lane shoot at" (see
    // Zombie.rowSpan), so this is the whole of what makes plants in BOTH rows able to hit it -- and
    // clearing every row first is what makes a shift safe rather than a way to leave the boss behind
    // in the rows it just left.
    private void fileBossInto(GameSession session, int topRow) {
        for (Row row : session.getMap().getRows()) {
            row.getZombies().remove(boss);
        }
        boss.getMovement().setPositionY(topRow);
        for (int lane : boss.occupiedRows()) {
            if (lane >= 0 && lane < session.getMap().getRows().size()) {
                session.getMap().getRow(lane).getZombies().add(boss);
            }
        }
    }

    // What the belt may deliver: the level's own plant list, which is the same pool every other level
    // draws its seed menu from -- so a boss level is authored exactly like its four predecessors and
    // the belt simply hands out what the player would otherwise have picked.
    private List<String> resolvePlantPool(GameSession session) {
        List<String> authored = session.getLevel() == null
                ? null : session.getLevel().getAvailablePlants();
        if (authored == null || authored.isEmpty()) {
            return new ArrayList<>(FALLBACK_PLANTS);
        }
        return new ArrayList<>(authored);
    }

    private String drawPlant() {
        return plantPool.get(random.nextInt(plantPool.size()));
    }

    // Ignoring case and surrounding space: the belt carries the level file's spelling and the click
    // that spends a card carries the card's, which are not guaranteed to be typed the same way.
    private int indexOf(String plantType) {
        if (plantType == null) {
            return -1;
        }
        String wanted = plantType.trim();
        for (int i = 0; i < conveyor.size(); i++) {
            if (conveyor.get(i).trim().equalsIgnoreCase(wanted)) {
                return i;
            }
        }
        return -1;
    }
}
