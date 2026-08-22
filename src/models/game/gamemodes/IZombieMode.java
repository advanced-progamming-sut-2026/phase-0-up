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
// them left to eat the brain in each row. Sun: one destructible maker per lane, plus plant bonuses.
public class IZombieMode extends StandardMode {

    // Zombies may only be summoned from this column rightward (the "red line").
    private static final int RED_LINE_COLUMN = 5;
    // Sun income: a DESTRUCTIBLE maker per lane drips sun, plus a bonus per plant broken.
    private static final int STARTING_SUN = 300;
    private static final String SUN_PRODUCER = "ZombieArmor2";     // the buckethead (Bucket, ~1290 HP)
    // One drop per maker per twenty seconds, worth a Sunflower's 25 -- see produceSun for why this is
    // the same income the old per-tick trickle gave.
    private static final int SUN_DROP_INTERVAL_TICKS = 20 * Constants.TICKS_PER_SECOND;
    private static final int SUN_DROP_AMOUNT = 25;
    // Long enough to cross the lawn for, short enough that an uncollected one is a real loss.
    private static final int SUN_DROP_EXPIRE_TICKS = 25 * Constants.TICKS_PER_SECOND;
    private static final int PLANT_DESTROYED_SUN_REWARD = 50;      // paid when the horde breaks a plant

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
    private long sunTick;       // drives the staggered sun drops; see produceSun
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
    public boolean countsTowardQuests() {
        return false;
    }
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
        session.increaseSunAmount(STARTING_SUN - session.getSunAmount());
        prePlacePlants(session);
        placeSunProducers(session);
        session.reportEvent("I, Zombie! You're on the undead side this time. Buy zombies with "
                + "\"summon -t <type> -l (x, y)\" -- right of column " + RED_LINE_COLUMN
                + " only -- and eat every brain. Your sun comes from the sun-maker zombies, not the "
                + "sky, so protect them. On the belt: " + String.join(", ", roster.keySet()) + ".");
    }

    @Override
    public void onTick(GameSession session) {
        produceSun(session);
        eatBrains(session);
        removeZombiesPastHouse(session);
    }
    @Override
    public void onPlantDestroyed(GameSession session, Plant plant) {
        session.increaseSunAmount(PLANT_DESTROYED_SUN_REWARD);
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

    // Sun makers DROP suns for the player to collect, exactly as a Sunflower does.
    //
    // It used to trickle straight into the bank, which made the whole mechanic invisible: the number
    // went up, and nothing on the board explained why or told the player which zombies were doing it.
    // A sun that lands on the lawn and has to be clicked is the same economy with the reason attached
    // -- and it is what makes "protect your sun-makers" mean something you can watch.
    //
    // The RATE is unchanged. Five makers used to yield 5 * 0.12 = 0.6 sun a tick, or 6 a second; one
    // 25-sun drop per maker every 20 seconds is 1.25 a second each, 6.25 across five. Balance held,
    // presentation replaced.
    private void produceSun(GameSession session) {
        sunTick++;
        int count = sunProducers.size();
        for (int i = 0; i < count; i++) {
            Zombie producer = sunProducers.get(i);
            if (producer.getHealth().isDead()) {
                continue;
            }
            // Staggered across the interval, so five lanes do not all flash a sun on the same tick.
            long offset = (long) i * SUN_DROP_INTERVAL_TICKS / Math.max(1, count);
            if ((sunTick + offset) % SUN_DROP_INTERVAL_TICKS != 0) {
                continue;
            }
            dropSun(session, producer);
        }
    }

    private void dropSun(GameSession session, Zombie producer) {
        int lane = producer.getMovement().getPositionY();
        // Not falling: this sun was never in the sky. Same reasoning as ProduceSunAbility -- flagging
        // it as falling would make it drop in from above the board instead of landing beside its maker.
        session.addSun(new models.entities.collectibles.Sun(producer.getX(), lane, lane,
                models.entities.collectibles.SunType.NORMAL, SUN_DROP_AMOUNT, false,
                SUN_DROP_EXPIRE_TICKS));
        session.reportEvent("A sun-maker drops " + SUN_DROP_AMOUNT + " sun in lane " + lane
                + ". Grab it before a plant shoots the maker down!");
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

    private void removeZombiesPastHouse(GameSession session) {
        for (Row row : session.getMap().getRows()) {
            row.getZombies().removeIf(z -> z.getX() <= 0 && !z.getHealth().isDead());
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
            placePlant(session, "Peashooter", 1, y);
            if (difficulty >= 2) {
                placePlant(session, "Wall-nut", 3, y);      // a wall to chew through
            }
            if (difficulty >= 3) {
                placePlant(session, "Snow Pea", 0, y);      // chills the horde, slowing every push
            }
            if (difficulty >= 4) {
                placePlant(session, "Repeater", 2, y);      // a second, harder-hitting shooter
            }
            if (difficulty >= 5) {
                placePlant(session, "Wall-nut", 4, y);      // a second wall, right at the red line
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

    // One buckethead maker per lane, pinned at speed 0 but a normal target the plants can kill.
    private void placeSunProducers(GameSession session) {
        int rows = session.getMap().getRows().size();
        int column = Constants.BOARD_COLS - 1;   // column 8, the far right edge
        for (int y = 0; y < rows; y++) {
            Zombie producer = ZombieFactory.createZombie(SUN_PRODUCER, column, y, session);
            if (producer == null) {
                continue;
            }
            producer.getMovement().setSpeed(0);   // stays put at column 8, but can be shot down
            session.getMap().getRow(y).getZombies().add(producer);
            sunProducers.add(producer);
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

    public boolean isBrainEaten(int lane) {
        return brainEaten != null && lane >= 0 && lane < brainEaten.length && brainEaten[lane];
    }

    // Which zombies are this level's sun makers. The view draws them as the game's disco mech rather
    // than as the plain buckethead the model spawns: a zombie that stands still and makes sun is doing
    // something no ordinary buckethead does, and it has to look like it.
    public boolean isSunProducer(Zombie zombie) {
        for (Zombie producer : sunProducers) {
            if (producer == zombie) {
                return true;
            }
        }
        return false;
    }

    public boolean isSummonable(String alias) {
        return matchRoster(alias) != null;
    }

    public int getDifficulty() {
        return difficulty;
    }
}
