package models.game.gamemodes;

import factories.ZombieFactory;
import models.entities.interactables.GargantuarVase;
import models.entities.interactables.PlantVase;
import models.entities.interactables.Vase;
import models.entities.interactables.VaseContent;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Row;
import models.templates.PlantTemplate;
import utils.Constants;
import utils.Result;
import utils.registry.PlantRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Vasebreaker mini-game. The board is seeded with clay vases; the player breaks them one by one, and
// each hides nothing, a zombie, or a one-use seed packet. There is no seed selection and no sky sun --
// every zombie and every plant comes out of a vase. The player wins by breaking every vase and
// clearing whatever zombies came out, and loses the instant a zombie reaches the house (no lawn mowers
// here). Two special vases are guaranteed each game: one GargantuarVase (always a Gargantuar) and one
// PlantVase (always a seed packet).
public class VaseBreakerMode extends StandardMode {

    // The vase band: every column from FIRST_VASE_COLUMN to the right edge is filled solid -- one vase
    // on every cell of it. On the standard 9-wide board that is columns 3-8 (30 vases); columns 0-2
    // hold no vases at all and start strictly empty as the player's build space.
    private static final int FIRST_VASE_COLUMN = 3;

    // Guaranteed special vases, placed before the ordinary ones. One Gargantuar vase (the doc's "giant
    // vase"), plus a dependable handful of plant vases so the player is never left without a supply of
    // seed packets on a board this dense.
    private static final int GARGANTUAR_VASES = 1;
    private static final int PLANT_VASES = 8;

    // Content odds for an ordinary "?" vase. An unknown vase is a threat first and a gift second: it
    // yields a zombie well over twice as often as a seed packet, so breaking one is a real gamble and
    // the player's plant supply leans on the guaranteed plant vases instead. Difficulty pushes the
    // zombie share higher still (and the armoured share with it, see randomZombieAlias).
    private static final double BASE_ZOMBIE_CHANCE = 0.45;
    private static final double ZOMBIE_CHANCE_PER_DIFFICULTY = 0.05;
    private static final double MAX_ZOMBIE_CHANCE = 0.65;
    private static final double SEED_CHANCE = 0.20;
    // A dropped seed packet fades this long after it lands, so the player must plant it quickly.
    private static final int SEED_FADE_TICKS = 10 * Constants.TICKS_PER_SECOND;
    // Plants tagged WATER need a flooded tile, which this lawn never has.
    private static final String WATER_TAG = "WATER";
    private static final String BASIC_ZOMBIE = "ZombieDefault";
    private static final String ARMORED_ZOMBIE = "ZombieArmor1";

    private final int difficulty;
    private final Random random;
    private final Map<Long, Vase> vases = new LinkedHashMap<>();
    private final Map<Long, DroppedSeed> droppedSeeds = new LinkedHashMap<>();
    private final List<String> plantPool = new ArrayList<>();
    // The player's Vasebreaker hand: plants collected from broken vases, waiting to be placed
    // (canonical plant name -> count). Detached from SeedPacket/selectedSeeds entirely.
    private final Map<String, Integer> hand = new LinkedHashMap<>();
    private boolean started;

    // A seed packet lying on the ground after a vase broke: which plant it grows and when it landed.
    private static final class DroppedSeed {
        final String plantType;
        final long dropTick;
        DroppedSeed(String plantType, long dropTick) { this.plantType = plantType; this.dropTick = dropTick; }
    }

    public VaseBreakerMode(int difficulty) {
        this(difficulty, new Random());
    }

    // Seeded variant so board generation is reproducible in a test.
    public VaseBreakerMode(int difficulty, Random random) {
        this.difficulty = Math.max(1, difficulty);
        this.random = random != null ? random : new Random();
    }

    // --- Mode contract ---------------------------------------------------------------------------

    @Override
    public void onStart(GameSession session) {
        if (started) {
            return;   // regenerating would smash a fresh board over the one the player is on
        }
        started = true;
        // Vasebreaker has no lawn mowers: a zombie that reaches the house ends the game outright.
        for (Row row : session.getMap().getRows()) {
            row.setLawnmower(null);
        }
        generateBoard(session);
    }

    // Drives the seed-packet timeout. Called every tick by GameSession.evaluateModeRules: a packet that
    // has lain on the grid for SEED_FADE_TICKS without being collected is destroyed and removed, and the
    // player is told so it never vanishes silently. This is the only thing that clears an uncollected
    // packet, so a dropped seed can never linger on the board indefinitely.
    @Override
    public void onTick(GameSession session) {
        long now = session.getTimeTicks();
        Iterator<Map.Entry<Long, DroppedSeed>> it = droppedSeeds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, DroppedSeed> entry = it.next();
            DroppedSeed seed = entry.getValue();
            if (!isFaded(seed, now)) {
                continue;
            }
            it.remove();
            session.reportEvent("The " + seed.plantType + " packet at ("
                    + columnOf(entry.getKey()) + ", " + rowOf(entry.getKey())
                    + ") withered away. Quicker next time!");
        }
    }

    // Won once every vase is broken and no zombie that came out is still standing.
    @Override
    public boolean checkWin(GameSession session) {
        return allBroken() && livingZombies(session) == 0;
    }

    // Lost the instant a living zombie reaches the house (x <= 0). There are no mowers to fall back on.
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
        return false;   // the player never picks a loadout -- plants come from vases
    }

    @Override
    public boolean allowsSkySun() {
        return false;   // no sun falls from the sky in Vasebreaker
    }

    // --- Player actions (called via GameSession) -------------------------------------------------

    // Breaks the vase at (x, y): a zombie bursts out, a seed packet drops, or it was empty.
    public Result breakVase(GameSession session, int x, int y) {
        Vase vase = vases.get(key(x, y));
        if (vase == null) {
            return new Result(false, "No vase at (" + x + ", " + y + ") -- you're swinging at air.");
        }
        if (vase.isBroken()) {
            return new Result(false, "That one's already in pieces.");
        }
        VaseContent content = vase.breakOpen();
        switch (content) {
            case ZOMBIE:
                Zombie zombie = ZombieFactory.createZombie(vase.getPayload(), x + 0.5, y, session);
                if (zombie == null) {
                    return new Result(true, "CRASH! Nothing but pottery shards.");
                }
                session.getMap().getRow(y).getZombies().add(zombie);
                return new Result(true, "CRASH! A " + zombie.getAlias() + " climbs out at ("
                        + x + ", " + y + ") -- deal with it before it reaches the house!");
            case SEED_PACKET:
            case PLANT:
                droppedSeeds.put(key(x, y), new DroppedSeed(vase.getPayload(), session.getTimeTicks()));
                return new Result(true, "CRASH! A " + vase.getPayload() + " seed packet tumbles out at ("
                        + x + ", " + y + "). Grab it before it withers!");
            case EMPTY:
            default:
                return new Result(true, "CRASH! Empty. Well, that was anticlimactic.");
        }
    }

    // Picks up the seed packet lying at (x, y) and adds that plant to the player's Vasebreaker hand.
    // Vasebreaker is fully detached from the standard seed-packet + sun economy: no SeedPacket is added
    // and no sun is granted. The plant simply becomes available to place once (see GameSession.plant,
    // which routes through this mode's inventory), and is removed from the hand the moment it is planted
    // so it stops showing up in "show plant status".
    public Result collectSeed(GameSession session, int x, int y) {
        long k = key(x, y);
        DroppedSeed seed = droppedSeeds.get(k);
        if (seed == null) {
            return new Result(false, "Nothing to pick up at (" + x + ", " + y + ").");
        }
        if (isFaded(seed, session.getTimeTicks())) {
            droppedSeeds.remove(k);
            return new Result(false, "Too slow -- that packet already withered away.");
        }
        droppedSeeds.remove(k);
        addToHand(seed.plantType);
        return new Result(true, "Got it! A " + seed.plantType + " is in your hand. "
                + "Plant it with: plant plant -t " + seed.plantType + " -l (x, y)");
    }

    // --- Plant inventory (the player's Vasebreaker "hand") ---------------------------------------
    // Plants only ever come from broken vases: collectSeed adds them here, planting removes them, and
    // "show plant status" reads them. Keyed by the plant's canonical registry name; lookups are
    // case-insensitive so the type the player types always matches.

    @Override
    public boolean managesPlantInventory() {
        return true;
    }

    @Override
    public boolean hasPlantAvailable(String plantType) {
        return handKey(plantType) != null;
    }

    @Override
    public void consumePlant(String plantType) {
        String key = handKey(plantType);
        if (key == null) {
            return;
        }
        int remaining = hand.getOrDefault(key, 0) - 1;
        if (remaining <= 0) {
            hand.remove(key);   // no longer available -> drops out of "show plant status"
        } else {
            hand.put(key, remaining);
        }
    }

    @Override
    public Map<String, Integer> plantInventory() {
        return Collections.unmodifiableMap(hand);
    }

    private void addToHand(String plantType) {
        hand.merge(canonicalPlantName(plantType), 1, Integer::sum);
    }

    // The hand key matching plantType ignoring case (checked against both the raw and canonical name),
    // or null when the player holds no such plant.
    private String handKey(String plantType) {
        if (plantType == null) {
            return null;
        }
        String raw = plantType.trim();
        String canonical = canonicalPlantName(plantType);
        for (String key : hand.keySet()) {
            if (key.equalsIgnoreCase(raw) || key.equalsIgnoreCase(canonical)) {
                return key;
            }
        }
        return null;
    }

    // Resolves a plant name to the registry's canonical spelling so a vase payload and a typed command
    // land on the same hand entry; falls back to the given name when the registry has no match.
    private String canonicalPlantName(String plantType) {
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(plantType);
        return (template != null && template.getName() != null) ? template.getName() : plantType;
    }

    // --- Board generation ------------------------------------------------------------------------

    private void generateBoard(GameSession session) {
        buildPlantPool();
        int rows = session.getMap().getRows().size();
        int cols = Constants.BOARD_COLS;

        // The vase band is filled solid: one vase on every cell from FIRST_VASE_COLUMN to the right
        // edge. Everything to the left of the band starts strictly empty -- that is the player's build
        // space, and it guarantees a zombie released from a vase always has lawn to cross before it
        // reaches the house (the game is lost at x <= 0).
        //
        // Difficulty does not change how many vases there are (the band is always full); it changes what
        // they hide -- see rollNormalVase -- so a harder board is deadlier rather than bigger.
        List<int[]> cells = new ArrayList<>();
        for (int y = 0; y < rows; y++) {
            for (int x = FIRST_VASE_COLUMN; x < cols; x++) {
                cells.add(new int[]{x, y});
            }
        }
        // Shuffled only so the two guaranteed special vases land anywhere in the band; every cell still
        // receives a vase either way.
        Collections.shuffle(cells, random);

        int placed = 0;
        // Guaranteed special vases first, so every game has its Gargantuar vase and its dependable
        // plant vases (the doc's two special-vase types) wherever the shuffle put them.
        for (int i = 0; i < GARGANTUAR_VASES && placed < cells.size(); i++) {
            int[] c = cells.get(placed++);
            addVase(new GargantuarVase(c[0], c[1]));
        }
        for (int i = 0; i < PLANT_VASES && placed < cells.size(); i++) {
            int[] c = cells.get(placed++);
            addVase(new PlantVase(c[0], c[1], randomPlant()));
        }
        // Every remaining cell of the band gets an ordinary "?" vase.
        for (; placed < cells.size(); placed++) {
            int[] c = cells.get(placed);
            addVase(rollNormalVase(c[0], c[1]));
        }
    }

    // An ordinary "?" vase: most often a seed packet, otherwise empty, and least often a zombie. Higher
    // difficulty shifts the odds towards zombies (capped at MAX_ZOMBIE_CHANCE) while the seed share
    // stays put, so the board gets harder without ever cutting off the player's plant supply.
    private Vase rollNormalVase(int x, int y) {
        double r = random.nextDouble();
        double zombieChance = Math.min(MAX_ZOMBIE_CHANCE,
                BASE_ZOMBIE_CHANCE + ZOMBIE_CHANCE_PER_DIFFICULTY * (difficulty - 1));
        if (r < zombieChance) {
            return new Vase(x, y, VaseContent.ZOMBIE, randomZombieAlias());
        }
        if (r < zombieChance + SEED_CHANCE) {
            return new Vase(x, y, VaseContent.SEED_PACKET, randomPlant());
        }
        return new Vase(x, y, VaseContent.EMPTY, null);
    }

    private String randomZombieAlias() {
        if (difficulty >= 2 && random.nextDouble() < 0.4) {
            return ARMORED_ZOMBIE;
        }
        return BASIC_ZOMBIE;
    }

    private void buildPlantPool() {
        plantPool.clear();
        for (PlantTemplate t : PlantRegistry.getInstance().getAllPlantTemplates().values()) {
            if (t.getName() != null && !t.getName().isBlank() && isUsableHere(t)) {
                plantPool.add(t.getName());
            }
        }
        if (plantPool.isEmpty()) {
            // Registry not loaded (e.g. a focused test): fall back to a small known pool.
            plantPool.add("Peashooter");
            plantPool.add("Wall-nut");
            plantPool.add("Cactus");
        }
    }

    // A vase only ever hands out a plant the player can actually put to work on this lawn:
    //   - sun producers are pointless here, because Vasebreaker has no sun economy at all (vase plants
    //     are free and no sun falls), so a Sunflower would just waste a packet the player raced for;
    //   - water plants and platforms (Lily Pad and friends) need a flooded tile, and the Vasebreaker
    //     lawn is entirely dry, so they could never be planted anywhere.
    private boolean isUsableHere(PlantTemplate template) {
        if (template.getCategory() == models.templates.PlantCategory.SUN_PRODUCER) {
            return false;
        }
        if (template.isPlatform()) {
            return false;
        }
        List<String> tags = template.getTags();
        return tags == null || !tags.contains(WATER_TAG);
    }

    private String randomPlant() {
        return plantPool.get(random.nextInt(plantPool.size()));
    }

    private void addVase(Vase vase) {
        vases.put(key(vase.getX(), vase.getY()), vase);
    }

    private boolean isFaded(DroppedSeed seed, long now) {
        return now - seed.dropTick >= SEED_FADE_TICKS;
    }

    private long key(int x, int y) {
        return (long) y * Constants.BOARD_COLS + x;
    }

    // Inverse of key(): recovers the cell a dropped-seed entry sits on.
    private int columnOf(long key) {
        return (int) (key % Constants.BOARD_COLS);
    }

    private int rowOf(long key) {
        return (int) (key / Constants.BOARD_COLS);
    }

    // --- Inspection (used by the map view and the verification harness) ---------------------------

    public Collection<Vase> getVases() {
        return Collections.unmodifiableCollection(vases.values());
    }

    public Vase getVaseAt(int x, int y) {
        return vases.get(key(x, y));
    }

    public int vaseCount() {
        return vases.size();
    }

    public boolean allBroken() {
        for (Vase vase : vases.values()) {
            if (!vase.isBroken()) {
                return false;
            }
        }
        return !vases.isEmpty();
    }

    public boolean hasDroppedSeed(int x, int y) {
        return droppedSeeds.containsKey(key(x, y));
    }

    public int droppedSeedCount() {
        return droppedSeeds.size();
    }

    public int getDifficulty() {
        return difficulty;
    }
}
