package models.game.gamemodes;

import factories.PlantFactory;
import factories.ZombieFactory;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Cell;
import models.map.Row;
import utils.Constants;
import utils.Result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// I, Zombie mini-game -- the game played from the zombies' side. The plants are AI-controlled and
// pre-placed on the left; the player spends sun to summon zombies to the RIGHT of a red line and march
// them left to eat the brain at the end of each row. Sun is produced by a special buckethead sun-maker
// zombie (one per row at start, not summonable) whose output grows over time. The player wins by eating
// every brain, and loses when they can no longer afford a zombie and none are left on the board.
public class IZombieMode extends StandardMode {

    // Zombies may only be summoned from this column rightward (the "red line").
    private static final int RED_LINE_COLUMN = 5;
    private static final int STARTING_SUN = 150;
    private static final String SUN_PRODUCER = "ZombieArmor1";   // buckethead: the un-summonable sun maker
    // Sun income per producer per tick = BASE + GROWTH * secondsElapsed, so it starts low and ramps up.
    private static final double SUN_BASE_RATE = 0.02;
    private static final double SUN_GROWTH_RATE = 0.003;
    private static final String[] PRE_PLACED_PLANTS = {"Peashooter", "Wall-nut"};

    // The 10-strong pool the level rosters are drawn from (alias -> summon cost). Each level shows a
    // different slice of 5 (chosen by difficulty), never the exact same five as another level.
    private static final String[] POOL_ALIASES = {
            "ZombieDefault", "ZombieImp", "ZombieRa", "ZombieExplorer",
            "ZombieArmor2", "ZombieTombRaiser", "ZombieGargantuar"
    };
    private static final int[] POOL_PRICES = {50, 25, 100, 125, 150, 175, 300};
    private static final int ROSTER_SIZE = 5;

    private final int difficulty;
    private final Random random;
    private final Map<String, Integer> roster = new LinkedHashMap<>();
    private final List<Zombie> sunProducers = new ArrayList<>();
    private boolean[] brainEaten;
    private double sunBudget;
    private boolean started;

    public IZombieMode(int difficulty) {
        this(difficulty, new Random());
    }

    // Seeded variant so plant placement and roster selection are reproducible in a test.
    public IZombieMode(int difficulty, Random random) {
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
        int rows = session.getMap().getRows().size();
        brainEaten = new boolean[rows];
        buildRoster();
        // A brain sits where the lawn mower would -- the mower itself is removed.
        for (Row row : session.getMap().getRows()) {
            row.setLawnmower(null);
        }
        // The zombie player begins with a fixed sun bank regardless of the level's default.
        session.increaseSunAmount(STARTING_SUN - session.getSunAmount());
        prePlacePlants(session);
        placeSunProducers(session);
    }

    // Produces the zombie player's sun and resolves any brain that just got eaten.
    @Override
    public void onTick(GameSession session) {
        produceSun(session);
        eatBrains(session);
    }

    // Won once every brain has been eaten.
    @Override
    public boolean checkWin(GameSession session) {
        if (brainEaten == null) {
            return false;
        }
        for (boolean eaten : brainEaten) {
            if (!eaten) {
                return false;
            }
        }
        return true;
    }

    // Lost when the player can no longer afford the cheapest zombie and none remain on the board.
    @Override
    public boolean checkLose(GameSession session) {
        return livingZombies(session) == 0 && session.getSunAmount() < cheapestPrice();
    }

    @Override
    public boolean requiresSeedSelection(GameSession session) {
        return false;
    }

    @Override
    public boolean allowsSkySun() {
        return false;   // the zombie player's sun comes from the sun-maker zombies, not the sky
    }

    // --- Player action: summon a zombie ----------------------------------------------------------

    public Result summonZombie(GameSession session, String type, int x, int y) {
        String alias = matchRoster(type);
        if (alias == null) {
            return new Result(false, "\"" + type + "\" is not one of your zombies this level. "
                    + "Available: " + String.join(", ", roster.keySet()) + ".");
        }
        if (x < RED_LINE_COLUMN || x >= Constants.BOARD_COLS) {
            return new Result(false, "Your horde masses right of the red line -- columns "
                    + RED_LINE_COLUMN + "-" + (Constants.BOARD_COLS - 1) + " only.");
        }
        if (y < 0 || y >= session.getMap().getRows().size()) {
            return new Result(false, "There's no lane " + y + " on this lawn.");
        }
        int price = roster.get(alias);
        if (session.getSunAmount() < price) {
            return new Result(false, alias + " costs " + price + " sun and you've only got "
                    + session.getSunAmount() + ". Let the sun-makers work!");
        }
        Zombie zombie = ZombieFactory.createZombie(alias, x, y, session);
        if (zombie == null) {
            return new Result(false, "\"" + alias + "\" wouldn't rise from the grave.");
        }
        session.decreaseSunAmount(price);
        session.getMap().getRow(y).getZombies().add(zombie);
        return new Result(true, "A " + alias + " lurches onto lane " + y + " for " + price
                + " sun. Go get those brainz!");
    }

    // --- Sun / brains ----------------------------------------------------------------------------

    private void produceSun(GameSession session) {
        int aliveProducers = 0;
        for (Zombie producer : sunProducers) {
            if (!producer.getHealth().isDead()) {
                aliveProducers++;
            }
        }
        if (aliveProducers == 0) {
            return;
        }
        double elapsedSeconds = session.getTimeTicks() / (double) Constants.TICKS_PER_SECOND;
        double ratePerProducer = SUN_BASE_RATE + SUN_GROWTH_RATE * elapsedSeconds;
        sunBudget += aliveProducers * ratePerProducer;
        int whole = (int) sunBudget;
        if (whole > 0) {
            session.increaseSunAmount(whole);
            sunBudget -= whole;
        }
    }

    private void eatBrains(GameSession session) {
        for (int y = 0; y < brainEaten.length; y++) {
            if (brainEaten[y]) {
                continue;
            }
            for (Zombie zombie : session.getMap().getRow(y).getZombies()) {
                if (!zombie.getHealth().isDead() && zombie.getX() <= 0) {
                    brainEaten[y] = true;   // a zombie reached the brain and ate it
                    break;
                }
            }
        }
    }

    // --- Setup helpers ---------------------------------------------------------------------------

    private void buildRoster() {
        roster.clear();
        int offset = (difficulty - 1) % POOL_ALIASES.length;
        for (int i = 0; i < ROSTER_SIZE; i++) {
            int idx = (offset + i) % POOL_ALIASES.length;
            roster.put(POOL_ALIASES[idx], POOL_PRICES[idx]);
        }
    }

    private void prePlacePlants(GameSession session) {
        int rows = session.getMap().getRows().size();
        for (int y = 0; y < rows; y++) {
            // One shooter per row (threatens the incoming zombies); tougher rows get a wall in front.
            placePlant(session, "Peashooter", 1 + random.nextInt(2), y);   // column 1 or 2
            if (difficulty >= 2) {
                placePlant(session, "Wall-nut", 3, y);
            }
        }
    }

    private void placePlant(GameSession session, String name, int x, int y) {
        if (!session.getMap().isValidCoordinate(x, y)) {
            return;
        }
        Plant plant = PlantFactory.createPlant(name, 1, x, y);
        if (plant == null) {
            return;
        }
        Cell cell = session.getMap().getCell(x, y);
        cell.addPlant(plant);
    }

    private void placeSunProducers(GameSession session) {
        int rows = session.getMap().getRows().size();
        for (int y = 0; y < rows; y++) {
            // One buckethead sun-maker per row, entering from the right edge.
            Zombie producer = ZombieFactory.createZombie(SUN_PRODUCER, Constants.BOARD_COLS - 1, y, session);
            if (producer != null) {
                session.getMap().getRow(y).getZombies().add(producer);
                sunProducers.add(producer);
            }
        }
    }

    private int cheapestPrice() {
        int min = Integer.MAX_VALUE;
        for (int price : roster.values()) {
            min = Math.min(min, price);
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private String matchRoster(String type) {
        if (type == null) {
            return null;
        }
        for (String alias : roster.keySet()) {
            if (alias.equalsIgnoreCase(type.trim())) {
                return alias;
            }
        }
        return null;
    }

    // --- Inspection (map view / verification harness) --------------------------------------------

    public Map<String, Integer> getRoster() {
        return new LinkedHashMap<>(roster);
    }

    public int getRedLineColumn() {
        return RED_LINE_COLUMN;
    }

    public int brainsTotal() {
        return brainEaten == null ? 0 : brainEaten.length;
    }

    public int brainsEaten() {
        if (brainEaten == null) {
            return 0;
        }
        int n = 0;
        for (boolean eaten : brainEaten) {
            if (eaten) {
                n++;
            }
        }
        return n;
    }

    public int aliveSunProducers() {
        int n = 0;
        for (Zombie producer : sunProducers) {
            if (!producer.getHealth().isDead()) {
                n++;
            }
        }
        return n;
    }

    public boolean isSummonable(String alias) {
        return matchRoster(alias) != null;
    }

    public int getDifficulty() {
        return difficulty;
    }
}
