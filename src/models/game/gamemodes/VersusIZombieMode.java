package models.game.gamemodes;

import factories.ZombieFactory;
import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.Faction;
import models.game.GameSession;
import models.map.Row;
import utils.Constants;
import utils.Result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Two-player I, Zombie: one person plants, the other person summons.
//
// It is IZombieMode's lawn with the AI taken out of the plant seat. The brains, the red line, the
// sun-maker zombies and the roster are the same rules; what changes is that the plants are chosen and
// placed by a human in real time, there is a clock, and the mode has to be able to say WHICH SIDE won
// rather than whether "the player" did.
//
// ## Not a subclass of IZombieMode -- on purpose
//
// GameSession.minigameName() (:379) dispatches on `instanceof IZombieMode`, so a versus win would be
// filed as a single-player "I, Zombie" clear and unlock a mini-game level on whichever profile the
// session held. The shared behaviour the views need is expressed as BrainLawn instead.
//
// ## The win-condition inversion -- read this before changing checkWin
//
// GameMode gives a mode exactly one checkWin/checkLose pair, and GameEngine.announceOutcome
// (GameEngine.java:141-160) turns those into two spec-verbatim banners. There is only one session and
// one pair of banners, but TWO players, and each of them needs the opposite one. So:
//
//   checkWin()  is true when the ZOMBIE player wins  (every brain eaten)
//   checkLose() is true when the PLANT player wins   (clock runs out, or the horde is spent)
//
// which matches single-player I, Zombie, where "the player" IS the zombie side. Neither banner is
// shown to anybody in a networked match: winner() is the source of truth, the server suppresses both
// strings, and each client renders the correct one for its own side (T3.7). Reading the GameState to
// decide who won would show one of the two players the wrong banner and is the single easiest mistake
// to make in this file.
//
// ## Two sun pools
//
// GameSession has one sunAmount field and there are two economies. sunAmount stays the PLANT player's
// bank -- so every existing cost check in GameSession.plant works untouched -- and the zombie player's
// bank lives here as zombieSun. Nothing may be spent from the wrong one, which is why summonZombie
// charges zombieSun directly rather than going through the session.
//
// A consequence worth stating: sun-maker income is credited straight to zombieSun instead of dropping
// a collectible the way IZombieMode does. A Sun on the board can only belong to one player, and in this
// mode the sky and the sunflowers are the plant player's -- so a maker's drop landing there would be
// collectable by the person it is meant to be used against.
public class VersusIZombieMode extends StandardMode implements BrainLawn {

    // How a match can finish, in the model's own words. Deliberately not net.dto.MatchEndReason: that
    // one also carries OPPONENT_LEFT, which is a fact about a socket and not about this lawn.
    public enum Ending {
        BRAINS_EATEN,   // the zombie player ate all five
        TIME_UP,        // the clock ran out with at least one brain standing
        HORDE_SPENT     // the zombie player has nothing on the board and cannot afford anything
    }

    private static final int RED_LINE_COLUMN = 5;

    // Three minutes. Long enough that a zombie summoned at the far right can actually walk nine columns
    // and reach a brain (the screenshot harness needs ~1200 ticks to see one eaten), short enough that
    // a stalemate ends rather than being abandoned.
    public static final int DEFAULT_DURATION_TICKS = 180 * Constants.TICKS_PER_SECOND;

    private static final int STARTING_PLANT_SUN = 175;
    private static final int STARTING_ZOMBIE_SUN = 250;

    // The zombie side's income, unchanged from IZombieMode: one destructible maker per lane, 25 sun
    // every twenty seconds each, plus a bonus every time the horde breaks a plant.
    private static final String SUN_PRODUCER = "ZombieArmor2";
    private static final int SUN_DROP_INTERVAL_TICKS = 20 * Constants.TICKS_PER_SECOND;
    private static final int SUN_DROP_AMOUNT = 25;
    private static final int PLANT_DESTROYED_SUN_REWARD = 50;

    // Fixed for both players, and identical every match -- no Random anywhere in this mode. Two clients
    // and a server all describe the same board, and a roster drawn at random would have to be sent over
    // the wire and trusted rather than simply agreed on.
    private static final String[] ZOMBIE_ROSTER = {
            "ZombieImp", "ZombieDefault", "ZombieRa", "ZombieExplorer", "ZombieGargantuar"
    };
    private static final int[] ZOMBIE_PRICES = {25, 50, 100, 125, 300};

    // What the plant player brings. Cheap, unambiguous and all placeable on a dry lawn: an income
    // plant, a wall, a mine, and three shooters. Handed over rather than chosen, because seed selection
    // is a menu one player would sit in while the other waited.
    private static final List<String> PLANT_ROSTER = List.of(
            "Sunflower", "Potato Mine", "Wall-nut", "Peashooter", "Repeater", "Snow Pea");

    private final int durationTicks;
    private final Map<String, Integer> roster = new LinkedHashMap<>();
    private final List<Zombie> sunProducers = new ArrayList<>();

    private boolean[] brainEaten;
    private int zombieSun;
    private long sunTick;
    private long startTick;
    private boolean started;
    private Faction winner;
    private Ending ending;

    public VersusIZombieMode() {
        this(DEFAULT_DURATION_TICKS);
    }

    public VersusIZombieMode(int durationTicks) {
        this.durationTicks = Math.max(1, durationTicks);
        // Built here rather than in onStart, unlike IZombieMode's. The server has to put both rosters
        // into MatchStart -- which is sent BEFORE the first tick, so that the clients have a board to
        // draw when the first snapshot lands -- and a roster that only exists after onStart would be
        // sent empty. It is a constant either way; there is nothing about it that needs a session.
        buildRoster();
    }

    // --- Mode contract ---------------------------------------------------------------------------

    @Override
    public boolean countsTowardQuests() {
        return false;   // a versus match is not campaign progress for either player
    }

    @Override
    public boolean requiresSeedSelection(GameSession session) {
        return false;   // both loadouts are fixed; see PLANT_ROSTER
    }

    @Override
    public List<String> preSelectedPlants() {
        return PLANT_ROSTER;
    }

    @Override
    public boolean allowsSkySun() {
        return true;    // unlike single-player I, Zombie: there is a plant player here who needs income
    }

    // The plant player is confined to their own half, exactly as the AI's pre-placed plants are in
    // IZombieMode. Without this they could wall off column 8 and the zombie player would have nowhere
    // legal to summon -- an unloseable position reached by typing one command.
    @Override
    public String plantingRefusal(int x, int y) {
        if (x >= RED_LINE_COLUMN) {
            return "Your garden ends at the red line -- columns 0-" + (RED_LINE_COLUMN - 1)
                    + " are yours to defend.";
        }
        return null;
    }

    @Override
    public void onStart(GameSession session) {
        if (started) {
            return;
        }
        started = true;
        startTick = session.getTimeTicks();
        int rows = session.getMap().getRows().size();
        brainEaten = new boolean[rows];

        // A brain sits where the lawn mower would. Same swap as IZombieMode, and for the same reason:
        // "the thing at the end of the lane" has to mean one thing to both players.
        for (Row row : session.getMap().getRows()) {
            row.setLawnmower(null);
        }

        session.increaseSunAmount(STARTING_PLANT_SUN - session.getSunAmount());
        zombieSun = STARTING_ZOMBIE_SUN;
        placeSunProducers(session);

        session.reportEvent("Versus I, Zombie! Plants hold columns 0-" + (RED_LINE_COLUMN - 1)
                + " and defend five brains; zombies buy from " + String.join(", ", roster.keySet())
                + " and summon right of column " + RED_LINE_COLUMN + ". "
                + (durationTicks / Constants.TICKS_PER_SECOND) + " seconds on the clock -- if a brain "
                + "is still standing when it runs out, the plants have held.");
    }

    @Override
    public void onTick(GameSession session) {
        produceSun();
        eatBrains(session);
        removeZombiesPastHouse(session);
        if (winner == null) {
            decideWinner(session);
        }
    }

    @Override
    public void onPlantDestroyed(GameSession session, Plant plant) {
        zombieSun += PLANT_DESTROYED_SUN_REWARD;   // the zombie player's bank, never the plant player's
    }

    @Override
    public boolean checkWin(GameSession session) {
        return winner == Faction.ZOMBIES;
    }

    @Override
    public boolean checkLose(GameSession session) {
        return winner == Faction.PLANTS;
    }

    // --- Outcome ---------------------------------------------------------------------------------

    // The only honest answer to "who won". checkWin/checkLose exist to drive GameState; this drives
    // what the two players are told.
    public Faction winner() {
        return winner;
    }

    public Ending ending() {
        return ending;
    }

    public int matchDurationTicks() {
        return durationTicks;
    }

    // Both banks, readable before onStart has run -- MatchStart carries them so each client's HUD shows
    // the right number on its very first frame instead of a zero that corrects itself a tick later.
    public int startingPlantSun() {
        return STARTING_PLANT_SUN;
    }

    public int startingZombieSun() {
        return STARTING_ZOMBIE_SUN;
    }

    public int ticksRemaining(GameSession session) {
        if (mirroredTicksRemaining != null) {
            return mirroredTicksRemaining;
        }
        if (!started || session == null) {
            return durationTicks;
        }
        long elapsed = session.getTimeTicks() - startTick;
        return (int) Math.max(0L, durationTicks - elapsed);
    }

    // --- Mirroring, for a networked client -------------------------------------------------------
    //
    // A client in a versus match holds a copy of this mode on a board it never simulates: the server
    // owns the rules, and the client is TOLD the clock, the zombie bank and which brains are gone.
    // Everything the views read off a BrainLawn has to keep working there, so those three facts are
    // written in from the snapshot rather than derived from a session that is not ticking.
    //
    // Null means "this is the authoritative board" -- the same "never set" trick Profile.volume uses,
    // and for the same reason: zero is a legitimate number of ticks remaining.

    private Integer mirroredTicksRemaining;

    public void mirror(int ticksRemaining, int zombieSun, boolean[] brains) {
        this.mirroredTicksRemaining = Math.max(0, ticksRemaining);
        this.zombieSun = zombieSun;
        if (brains != null && brains.length > 0) {
            this.brainEaten = brains.clone();
        }
    }

    public boolean isMirrored() {
        return mirroredTicksRemaining != null;
    }

    // Which of the mirrored zombies are the sun makers. The client cannot work this out -- they are
    // ordinary bucketheads and the mode that designated them is running on the server -- so the
    // snapshot carries a flag and the reconciler relays it here, where ZombieRenderer already asks.
    public void markSunProducer(Zombie zombie) {
        if (zombie != null && !isSunProducer(zombie)) {
            sunProducers.add(zombie);
        }
    }

    public void forgetSunProducer(Zombie zombie) {
        sunProducers.removeIf(producer -> producer == zombie);
    }

    private void decideWinner(GameSession session) {
        if (allBrainsEaten()) {
            winner = Faction.ZOMBIES;
            ending = Ending.BRAINS_EATEN;
            return;
        }
        if (ticksRemaining(session) <= 0) {
            winner = Faction.PLANTS;
            ending = Ending.TIME_UP;
            return;
        }
        if (hordeSpent(session)) {
            winner = Faction.PLANTS;
            ending = Ending.HORDE_SPENT;
        }
    }

    private boolean allBrainsEaten() {
        if (brainEaten == null || brainEaten.length == 0) {
            return false;
        }
        for (boolean eaten : brainEaten) {
            if (!eaten) {
                return false;
            }
        }
        return true;
    }

    // Broke with nothing left walking. The sun makers are excluded from the count deliberately: they
    // never advance and never eat, so counting them would mean this condition could not fire until the
    // plant player had shot every one of them down -- by which point the zombie player has no income
    // either and the match is decided anyway, just several silent minutes later.
    private boolean hordeSpent(GameSession session) {
        for (Row row : session.getMap().getRows()) {
            for (Zombie zombie : row.getZombies()) {
                if (!zombie.getHealth().isDead() && !isSunProducer(zombie)) {
                    return false;
                }
            }
        }
        return zombieSun < cheapestPrice();
    }

    // --- The zombie player's action --------------------------------------------------------------

    @Override
    public Result summonZombie(GameSession session, String type, int x, int y) {
        String alias = matchRoster(type);
        if (alias == null) {
            return new Result(false, "\"" + type + "\" is not on your belt this match. "
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
        if (zombieSun < price) {
            return new Result(false, alias + " costs " + price + " sun and you've only got "
                    + zombieSun + ". Let the sun-makers work!");
        }
        Zombie zombie = ZombieFactory.createZombie(alias, x, y, session);
        if (zombie == null) {
            return new Result(false, "\"" + alias + "\" wouldn't rise from the grave.");
        }
        zombieSun -= price;
        session.getMap().getRow(y).getZombies().add(zombie);
        return new Result(true, "A " + alias + " lurches onto lane " + y + " for " + price
                + " sun. Go get those brainz!");
    }

    // --- Sun / brains ----------------------------------------------------------------------------

    public int getZombieSun() {
        return zombieSun;
    }

    // Credited straight to the bank rather than dropped on the lawn -- see the class comment. The rate
    // is IZombieMode's: 25 sun per living maker every twenty seconds, staggered so five lanes do not
    // all pay out on the same tick.
    private void produceSun() {
        sunTick++;
        int count = sunProducers.size();
        for (int i = 0; i < count; i++) {
            Zombie producer = sunProducers.get(i);
            if (producer.getHealth().isDead()) {
                continue;
            }
            long offset = (long) i * SUN_DROP_INTERVAL_TICKS / Math.max(1, count);
            if ((sunTick + offset) % SUN_DROP_INTERVAL_TICKS != 0) {
                continue;
            }
            zombieSun += SUN_DROP_AMOUNT;
        }
    }

    private void eatBrains(GameSession session) {
        for (int y = 0; y < brainEaten.length; y++) {
            if (brainEaten[y]) {
                continue;
            }
            for (Zombie zombie : session.getMap().getRow(y).getZombies()) {
                if (!zombie.getHealth().isDead() && zombie.getX() <= 0) {
                    brainEaten[y] = true;
                    session.reportEvent("The brain in lane " + y + " is gone. "
                            + brainsEaten() + " of " + brainsTotal() + " eaten.");
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
        for (int i = 0; i < ZOMBIE_ROSTER.length; i++) {
            roster.put(ZOMBIE_ROSTER[i], ZOMBIE_PRICES[i]);
        }
    }

    private void placeSunProducers(GameSession session) {
        int rows = session.getMap().getRows().size();
        int column = Constants.BOARD_COLS - 1;
        for (int y = 0; y < rows; y++) {
            Zombie producer = ZombieFactory.createZombie(SUN_PRODUCER, column, y, session);
            if (producer == null) {
                continue;
            }
            producer.getMovement().setSpeed(0);   // stands at column 8, but the plants can shoot it down
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

    // --- BrainLawn (what the views read) ---------------------------------------------------------

    @Override
    public Map<String, Integer> getRoster() {
        return new LinkedHashMap<>(roster);
    }

    @Override
    public int getRedLineColumn() {
        return RED_LINE_COLUMN;
    }

    @Override
    public int brainsTotal() {
        return brainEaten == null ? 0 : brainEaten.length;
    }

    @Override
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

    @Override
    public boolean isBrainEaten(int lane) {
        return brainEaten != null && lane >= 0 && lane < brainEaten.length && brainEaten[lane];
    }

    @Override
    public boolean isSunProducer(Zombie zombie) {
        for (Zombie producer : sunProducers) {
            if (producer == zombie) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isSummonable(String alias) {
        return matchRoster(alias) != null;
    }

    // Which brains are still standing, for the snapshot. A copy: the array is the match's state and the
    // network layer must not be able to write to it.
    public boolean[] brainState() {
        return brainEaten == null ? new boolean[0] : brainEaten.clone();
    }
}
