package models.game.gamemodes;

import factories.PlantFactory;
import factories.ZombieFactory;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Cell;
import models.map.Row;
import models.minigames.Upgrade;
import utils.Constants;
import utils.Result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Beghouled mini-game -- a match-3 played on the lawn. The board starts full of five random plant
// types; the player swaps two adjacent plants, but only when the swap forms a line of three or more of
// one type. A match clears, the plants above fall, and random new plants drop in from the top, which
// can set off cascades. Each match pays sun (50 x (size-2), +50 for a cascade match) that the player
// spends on plant upgrades. Meanwhile zombies keep coming as in a normal level; a zombie that eats a
// plant leaves a permanent crater. The player wins by making a target number of matches (which then
// wipes out every zombie) and loses if a zombie reaches the house.
public class BeghouledMode extends StandardMode {

    private static final String[] BASE_TYPES =
            {"Peashooter", "Sunflower", "Wall-nut", "Puff-shroom", "Cabbage-pult"};
    private static final int SUN_PER_UNIT = 50;
    private static final int BASE_TARGET = 5;
    private static final int TARGET_PER_DIFFICULTY = 5;
    private static final int SPAWN_INTERVAL_TICKS = 6 * Constants.TICKS_PER_SECOND;
    private static final String BASIC_ZOMBIE = "ZombieDefault";
    private static final String ARMORED_ZOMBIE = "ZombieArmor1";

    private final int difficulty;
    private final Random random;
    private final Map<String, Upgrade> upgrades = new LinkedHashMap<>();
    private int rows;
    private int cols;
    private String[][] type;         // plant type per cell (null = momentarily empty during a resolve)
    private boolean[][] crater;      // a cell a zombie hollowed out -- no plant may ever sit here again
    private boolean[][] hasCombatPlant;   // cells where a real, zombie-fighting Plant is currently placed
    private int matchTarget;
    private int matchesMade;
    private long spawnTimer;
    private boolean started;

    public BeghouledMode(int difficulty) {
        this(difficulty, new Random());
    }

    // Seeded variant so board generation and refills are reproducible in a test.
    public BeghouledMode(int difficulty, Random random) {
        this.difficulty = Math.max(1, difficulty);
        this.random = random != null ? random : new Random();
        buildUpgrades();
    }

    // --- Mode contract ---------------------------------------------------------------------------

    @Override
    public void onStart(GameSession session) {
        if (started) {
            return;
        }
        started = true;
        rows = session.getMap().getRows().size();
        cols = Constants.BOARD_COLS;
        type = new String[rows][cols];
        crater = new boolean[rows][cols];
        hasCombatPlant = new boolean[rows][cols];
        matchTarget = BASE_TARGET + (difficulty - 1) * TARGET_PER_DIFFICULTY;
        for (Row row : session.getMap().getRows()) {
            row.setLawnmower(null);
        }
        randomizeBoard();
        settleWithoutReward();          // clear any accidental starting matches, no sun for free
        ensurePlayable();
        syncMap(session);
    }

    @Override
    public void onTick(GameSession session) {
        markEatenPlantsAsCraters(session);
        if (checkWin(session)) {
            clearAllZombies(session);   // reaching the match target wipes the garden of zombies
            return;
        }
        spawnTimer++;
        if (spawnTimer >= SPAWN_INTERVAL_TICKS) {
            spawnZombie(session);
            spawnTimer = 0;
        }
    }

    @Override
    public boolean checkWin(GameSession session) {
        return matchesMade >= matchTarget;
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
        return false;   // sun comes only from making matches
    }

    // --- Player actions --------------------------------------------------------------------------

    // Swaps two adjacent plants. Allowed only if the swap forms a match; otherwise it is undone.
    public Result swap(GameSession session, int r1, int c1, int r2, int c2) {
        if (!inBounds(r1, c1) || !inBounds(r2, c2)) {
            return new Result(false, "That's off the lawn.");
        }
        if (Math.abs(r1 - r2) + Math.abs(c1 - c2) != 1) {
            return new Result(false, "Only next-door neighbours can trade places.");
        }
        if (crater[r1][c1] || crater[r2][c2] || type[r1][c1] == null || type[r2][c2] == null) {
            return new Result(false, "Nothing to swap there -- one of those tiles is a crater.");
        }
        swapTypes(r1, c1, r2, c2);
        if (findRuns().isEmpty()) {
            swapTypes(r1, c1, r2, c2);   // no match -> undo
            return new Result(false, "No match from that one. The plants shuffle back, unimpressed.");
        }
        int gained = resolveBoard(session);
        syncMap(session);
        ensurePlayable();
        syncMap(session);
        return new Result(true, "Match! " + gained + " sun banked (" + matchesMade + "/"
                + matchTarget + " matches).");
    }

    // Spends sun to upgrade every plant of one type on the board to its next form.
    public Result upgrade(GameSession session, String fromType) {
        Upgrade up = fromType == null ? null : upgrades.get(fromType.toLowerCase().trim());
        if (up == null) {
            return new Result(false, "\"" + fromType + "\" has nowhere left to grow.");
        }
        int count = countType(up.getFromPlant());
        if (count == 0) {
            return new Result(false, "Not a single " + up.getFromPlant() + " on the lawn to upgrade.");
        }
        if (session.getSunAmount() < up.getCost()) {
            return new Result(false, "Need " + up.getCost() + " sun for that upgrade -- you've got "
                    + session.getSunAmount() + ". Go make some matches!");
        }
        session.decreaseSunAmount(up.getCost());
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (type[r][c] != null && type[r][c].equalsIgnoreCase(up.getFromPlant())) {
                    type[r][c] = up.getToPlant();
                }
            }
        }
        syncMap(session);
        return new Result(true, "Kaboom! All " + count + " " + up.getFromPlant() + " grew up into "
                + up.getToPlant() + " for " + up.getCost() + " sun.");
    }

    // --- Match engine ----------------------------------------------------------------------------

    // Every maximal horizontal or vertical run of 3+ identical plants, as lists of {row, col} cells.
    public List<List<int[]>> findRuns() {
        List<List<int[]>> runs = new ArrayList<>();
        // horizontal
        for (int r = 0; r < rows; r++) {
            int c = 0;
            while (c < cols) {
                if (type[r][c] == null || crater[r][c]) { c++; continue; }
                int start = c;
                while (c + 1 < cols && !crater[r][c + 1] && type[r][c].equals(type[r][c + 1])) {
                    c++;
                }
                if (c - start + 1 >= 3) {
                    runs.add(cells(r, start, r, c));
                }
                c++;
            }
        }
        // vertical
        for (int c = 0; c < cols; c++) {
            int r = 0;
            while (r < rows) {
                if (type[r][c] == null || crater[r][c]) { r++; continue; }
                int start = r;
                while (r + 1 < rows && !crater[r + 1][c] && type[r][c].equals(type[r + 1][c])) {
                    r++;
                }
                if (r - start + 1 >= 3) {
                    runs.add(cells(start, c, r, c));
                }
                r++;
            }
        }
        return runs;
    }

    // Resolves the board after a match-forming move: repeatedly clears runs, drops plants and refills,
    // paying sun for every run. The first pass is the player's own match; later passes are cascades and
    // pay one extra sun each. Returns the total sun granted.
    private int resolveBoard(GameSession session) {
        int gained = 0;
        boolean cascade = false;
        while (true) {
            List<List<int[]>> runs = findRuns();
            if (runs.isEmpty()) {
                break;
            }
            for (List<int[]> run : runs) {
                matchesMade++;
                gained += sunValue(run.size(), cascade);
                for (int[] cell : run) {
                    type[cell[0]][cell[1]] = null;
                }
            }
            collapse();
            refill();
            cascade = true;
        }
        if (gained > 0) {
            session.increaseSunAmount(gained);
        }
        return gained;
    }

    // Sun paid for one run: 50 x (size-2), plus a 50 cascade bonus for matches formed by the refill.
    public static int sunValue(int runSize, boolean cascade) {
        return (Math.max(0, runSize - 2) + (cascade ? 1 : 0)) * SUN_PER_UNIT;
    }

    // Drops every plant to the bottom of its column, stopping above craters (which stay put). Public so
    // the map view (and the harness) can settle a column; refill is separate.
    public void collapse() {
        for (int c = 0; c < cols; c++) {
            int segTop = 0;
            for (int r = 0; r <= rows; r++) {
                if (r == rows || crater[r][c]) {
                    compactSegment(segTop, r - 1, c);
                    segTop = r + 1;
                }
            }
        }
    }

    // Slides the non-empty plants in one crater-free column segment down to its bottom.
    private void compactSegment(int top, int bottom, int c) {
        if (top > bottom) {
            return;
        }
        List<String> plants = new ArrayList<>();
        for (int r = top; r <= bottom; r++) {
            if (type[r][c] != null) {
                plants.add(type[r][c]);
            }
        }
        int r = bottom;
        for (int i = plants.size() - 1; i >= 0; i--) {
            type[r--][c] = plants.get(i);
        }
        while (r >= top) {
            type[r--][c] = null;
        }
    }

    private void refill() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!crater[r][c] && type[r][c] == null) {
                    type[r][c] = randomType();
                }
            }
        }
    }

    // Whether any single adjacent swap anywhere would form a match.
    public boolean hasAnyValidMove() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (createsMatchBySwap(r, c, r, c + 1) || createsMatchBySwap(r, c, r + 1, c)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean createsMatchBySwap(int r1, int c1, int r2, int c2) {
        if (!inBounds(r1, c1) || !inBounds(r2, c2)) {
            return false;
        }
        if (crater[r1][c1] || crater[r2][c2] || type[r1][c1] == null || type[r2][c2] == null) {
            return false;
        }
        swapTypes(r1, c1, r2, c2);
        boolean match = !findRuns().isEmpty();
        swapTypes(r1, c1, r2, c2);
        return match;
    }

    // Re-randomizes every non-crater cell (the "no moves left" reset) until a move exists again.
    public void resetBoard(GameSession session) {
        int attempts = 0;
        do {
            randomizeBoard();
            settleWithoutReward();
            attempts++;
        } while (!hasAnyValidMove() && attempts < 30);
        if (session != null) {
            syncMap(session);
        }
    }

    // --- Board / zombie helpers ------------------------------------------------------------------

    private void ensurePlayable() {
        if (!hasAnyValidMove()) {
            resetBoard(null);
        }
    }

    private void randomizeBoard() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                type[r][c] = crater[r][c] ? null : randomType();
            }
        }
    }

    // Clears any matches already on the board without paying sun or counting them (used at start and
    // after a reset, so a freshly dealt board never hands out free sun).
    private void settleWithoutReward() {
        while (true) {
            List<List<int[]>> runs = findRuns();
            if (runs.isEmpty()) {
                break;
            }
            for (List<int[]> run : runs) {
                for (int[] cell : run) {
                    type[cell[0]][cell[1]] = null;
                }
            }
            collapse();
            refill();
        }
    }

    private void markEatenPlantsAsCraters(GameSession session) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (hasCombatPlant[r][c] && !session.getMap().getCell(c, r).hasPlant()) {
                    crater[r][c] = true;      // a zombie ate the plant here
                    type[r][c] = null;
                    hasCombatPlant[r][c] = false;
                }
            }
        }
    }

    private void spawnZombie(GameSession session) {
        int row = random.nextInt(rows);
        String alias = difficulty >= 2 && random.nextDouble() < 0.3 ? ARMORED_ZOMBIE : BASIC_ZOMBIE;
        Zombie zombie = ZombieFactory.createZombie(alias, cols - 1, row, session);
        if (zombie != null) {
            session.getMap().getRow(row).getZombies().add(zombie);
        }
    }

    private void clearAllZombies(GameSession session) {
        for (Row row : session.getMap().getRows()) {
            for (Zombie zombie : new ArrayList<>(row.getZombies())) {
                zombie.getHealth().applyDamage(zombie.getHealth().getTotalHP(),
                        models.entities.projectiles.Element.NEUTRAL, null);
            }
        }
    }

    // Rebuilds the real, zombie-fighting Plant on every cell so it matches the logical board.
    private void syncMap(GameSession session) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = session.getMap().getCell(c, r);
                if (crater[r][c] || type[r][c] == null) {
                    if (cell.hasPlant()) {
                        cell.removePlant();
                    }
                    hasCombatPlant[r][c] = false;
                    continue;
                }
                Plant current = cell.getCurrentPlant();
                if (current != null && type[r][c].equalsIgnoreCase(current.getName())) {
                    continue;   // already the right plant
                }
                if (cell.hasPlant()) {
                    cell.removePlant();
                }
                Plant plant = PlantFactory.createPlant(type[r][c], 1, c, r);
                hasCombatPlant[r][c] = plant != null && cell.addPlant(plant).success();
            }
        }
    }

    private void buildUpgrades() {
        addUpgrade("Peashooter", "Repeater", 500);
        addUpgrade("Repeater", "Mega Gatling Pea", 1500);
        addUpgrade("Wall-nut", "Tall-nut", 500);
        addUpgrade("Puff-shroom", "Fume-shroom", 250);
        addUpgrade("Cabbage-pult", "Melon-pult", 1000);
        addUpgrade("Melon-pult", "Winter Melon", 750);
    }

    private void addUpgrade(String from, String to, int cost) {
        upgrades.put(from.toLowerCase(), new Upgrade(from, to, cost));
    }

    private int countType(String plantType) {
        int n = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (type[r][c] != null && type[r][c].equalsIgnoreCase(plantType)) {
                    n++;
                }
            }
        }
        return n;
    }

    private List<int[]> cells(int r1, int c1, int r2, int c2) {
        List<int[]> list = new ArrayList<>();
        for (int r = r1; r <= r2; r++) {
            for (int c = c1; c <= c2; c++) {
                list.add(new int[]{r, c});
            }
        }
        return list;
    }

    private void swapTypes(int r1, int c1, int r2, int c2) {
        String tmp = type[r1][c1];
        type[r1][c1] = type[r2][c2];
        type[r2][c2] = tmp;
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < cols;
    }

    private String randomType() {
        return BASE_TYPES[random.nextInt(BASE_TYPES.length)];
    }

    // --- Inspection (map view / verification harness) --------------------------------------------

    public String[][] board() { return type; }
    public boolean[][] craters() { return crater; }
    public String typeAt(int r, int c) { return type[r][c]; }
    public boolean isCrater(int r, int c) { return crater[r][c]; }
    public int getMatchesMade() { return matchesMade; }
    public int getMatchTarget() { return matchTarget; }
    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public Map<String, Upgrade> getUpgrades() { return new LinkedHashMap<>(upgrades); }

    public List<Integer> findRunSizes() {
        List<Integer> sizes = new ArrayList<>();
        for (List<int[]> run : findRuns()) {
            sizes.add(run.size());
        }
        return sizes;
    }
}
