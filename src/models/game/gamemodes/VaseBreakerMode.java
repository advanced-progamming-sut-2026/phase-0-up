package models.game.gamemodes;

import factories.ZombieFactory;
import models.entities.interactables.GargantuarVase;
import models.entities.interactables.PlantVase;
import models.entities.interactables.Vase;
import models.entities.interactables.VaseContent;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.SeedPacket;
import models.map.Row;
import models.templates.PlantTemplate;
import utils.Constants;
import utils.Result;
import utils.registry.PlantRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

    // Board sizing: a base count of vases plus a few more per difficulty tier.
    private static final int BASE_VASES = 8;
    private static final int VASES_PER_DIFFICULTY = 3;
    // A dropped seed packet fades this long after it lands, so the player must plant it quickly.
    private static final int SEED_FADE_TICKS = 10 * Constants.TICKS_PER_SECOND;
    // Vase seed packets carry no recharge -- they are gated by the free sun granted on pickup, not a
    // cooldown (see collectSeed).
    private static final int VASE_SEED_RECHARGE = 0;
    private static final String BASIC_ZOMBIE = "ZombieDefault";
    private static final String ARMORED_ZOMBIE = "ZombieArmor1";

    private final int difficulty;
    private final Random random;
    private final Map<Long, Vase> vases = new LinkedHashMap<>();
    private final Map<Long, DroppedSeed> droppedSeeds = new LinkedHashMap<>();
    private final List<String> plantPool = new ArrayList<>();
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

    // Drives the seed-packet fade. Called each tick by GameSession.evaluateModeRules.
    @Override
    public void onTick(GameSession session) {
        long now = session.getTimeTicks();
        droppedSeeds.entrySet().removeIf(e -> isFaded(e.getValue(), now));
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
            return new Result(false, "There is no vase at (" + x + ", " + y + ").");
        }
        if (vase.isBroken()) {
            return new Result(false, "The vase at (" + x + ", " + y + ") is already broken.");
        }
        VaseContent content = vase.breakOpen();
        switch (content) {
            case ZOMBIE:
                Zombie zombie = ZombieFactory.createZombie(vase.getPayload(), x + 0.5, y, session);
                if (zombie == null) {
                    return new Result(true, "You smashed the vase at (" + x + ", " + y + "); it was empty.");
                }
                session.getMap().getRow(y).getZombies().add(zombie);
                return new Result(true, "A " + zombie.getAlias() + " burst out of the vase at ("
                        + x + ", " + y + ")! Deal with it before it reaches your house.");
            case SEED_PACKET:
            case PLANT:
                droppedSeeds.put(key(x, y), new DroppedSeed(vase.getPayload(), session.getTimeTicks()));
                return new Result(true, "A seed packet (" + vase.getPayload() + ") dropped at ("
                        + x + ", " + y + "); collect it before it fades!");
            case EMPTY:
            default:
                return new Result(true, "The vase at (" + x + ", " + y + ") was empty.");
        }
    }

    // Picks up the seed packet lying at (x, y). Vasebreaker plants are free, so this grants exactly the
    // plant's sun cost and adds the packet to the loadout -- the existing plant() pipeline then places
    // it at no net cost. The granted sun is what gates how many free plants the player gets.
    public Result collectSeed(GameSession session, int x, int y) {
        long k = key(x, y);
        DroppedSeed seed = droppedSeeds.get(k);
        if (seed == null) {
            return new Result(false, "There is no seed packet to collect at (" + x + ", " + y + ").");
        }
        if (isFaded(seed, session.getTimeTicks())) {
            droppedSeeds.remove(k);
            return new Result(false, "The seed packet at (" + x + ", " + y + ") has already faded away.");
        }
        droppedSeeds.remove(k);
        PlantTemplate template = PlantRegistry.getInstance().getTemplateByName(seed.plantType);
        int cost = template != null ? (int) Math.round(template.getCost()) : 0;
        session.increaseSunAmount(cost);
        if (!session.isSeedSelected(seed.plantType)) {
            session.addSeed(new SeedPacket(seed.plantType, VASE_SEED_RECHARGE));
        }
        return new Result(true, "You collected a " + seed.plantType + " seed packet. Plant it with: "
                + "plant plant -t " + seed.plantType + " -l (" + x + ", " + y + ")");
    }

    // --- Board generation ------------------------------------------------------------------------

    private void generateBoard(GameSession session) {
        buildPlantPool();
        int rows = session.getMap().getRows().size();
        int cols = Constants.BOARD_COLS;
        int target = Math.min(rows * cols, BASE_VASES + (difficulty - 1) * VASES_PER_DIFFICULTY);

        List<int[]> cells = new ArrayList<>();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                cells.add(new int[]{x, y});
            }
        }
        Collections.shuffle(cells, random);

        int placed = 0;
        // Guarantee the two special vases first, so every game has exactly one Gargantuar vase and one
        // plant vase (the doc's two special-vase types).
        if (target >= 2) {
            int[] g = cells.get(placed++);
            addVase(new GargantuarVase(g[0], g[1]));
            int[] p = cells.get(placed++);
            addVase(new PlantVase(p[0], p[1], randomPlant()));
        }
        for (; placed < target; placed++) {
            int[] c = cells.get(placed);
            addVase(rollNormalVase(c[0], c[1]));
        }
    }

    // A normal "?" vase: mostly a zombie or empty, sometimes a seed packet. Higher difficulty packs in
    // more zombies.
    private Vase rollNormalVase(int x, int y) {
        double r = random.nextDouble();
        double zombieChance = Math.min(0.6, 0.30 + 0.05 * (difficulty - 1));
        double seedChance = 0.25;
        if (r < zombieChance) {
            return new Vase(x, y, VaseContent.ZOMBIE, randomZombieAlias());
        }
        if (r < zombieChance + seedChance) {
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
            if (t.getName() != null && !t.getName().isBlank()) {
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
