package models.game.gamemodes;

import factories.MinigameFactory;
import factories.ZombieFactory;
import models.game.Faction;
import models.game.GameSession;
import models.game.GameState;
import models.entities.zombies.Zombie;
import models.game.Level;
import models.map.Row;
import models.user.Profile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.Constants;
import utils.Result;
import utils.gameinitializers.GameInitializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The two-player mode's rules, checked without a socket in sight.
//
// Everything here is a thing that is invisible when it breaks. A win credited to the wrong faction
// still ends the match; a shared sun bank still lets both players spend; a plant placed at column 8
// still draws correctly. The only place any of it shows up is in what the two players are told at the
// end -- by which point it is far too late to debug.
class VersusIZombieModeTest {

    @BeforeAll
    static void loadGameData() {
        new GameInitializer().loadAllData();
    }

    private static GameSession started(int durationTicks) {
        Level level = new Level(new models.game.Wave[0], null, new VersusIZombieMode(durationTicks),
                0, new java.util.ArrayList<>(), 0, Constants.DEFAULT_SEED_SLOTS, null);
        GameSession session = new GameSession(new Profile(), level);
        session.startMode();
        return session;
    }

    private static GameSession started() {
        return started(VersusIZombieMode.DEFAULT_DURATION_TICKS);
    }

    private static VersusIZombieMode modeOf(GameSession session) {
        return (VersusIZombieMode) session.getMode();
    }

    // Past the opening grace, so a test about summoning is about summoning.
    //
    // Every test below that buys a zombie goes through here rather than starting the clock at zero,
    // because the mode now refuses everything for the first twenty seconds and a test that did not know
    // that would fail with a message about digging.
    private static GameSession openPlay() {
        GameSession session = started();
        session.advanceTime(modeOf(session).graceTicks());
        return session;
    }

    // The cheapest thing on the belt, asked rather than typed: the price table is a balance knob and
    // a test that hard-codes 25 goes quietly wrong the next time somebody turns it.
    private static int cheapest(VersusIZombieMode mode) {
        return mode.getRoster().values().stream().min(Integer::compareTo).orElseThrow();
    }

    // --- Setup ------------------------------------------------------------------------------------

    @Test
    void theFactoryBuildsTheSameBoardEveryTime() {
        // No difficulty, no seed, no Random: the server and both clients build this level separately
        // and have to agree on it without exchanging a word.
        VersusIZombieMode a = (VersusIZombieMode) MinigameFactory.createVersusIZombie().getGameMode();
        VersusIZombieMode b = (VersusIZombieMode) MinigameFactory.createVersusIZombie().getGameMode();
        assertFalse(a.getRoster().isEmpty(),
                "the roster has to exist before onStart -- MatchStart carries it to both clients");
        assertEquals(a.getRoster(), b.getRoster());
        assertEquals(a.matchDurationTicks(), b.matchDurationTicks());
        assertEquals(a.preSelectedPlants(), b.preSelectedPlants());
    }

    @Test
    void brainsReplaceTheMowersAndBothPlayersStartWithSeeds() {
        GameSession session = started();
        VersusIZombieMode mode = modeOf(session);

        assertEquals(session.getMap().getRows().size(), mode.brainsTotal());
        assertEquals(0, mode.brainsEaten());
        for (Row row : session.getMap().getRows()) {
            assertNull(row.getLawnmower(), "the brain stands where the mower did");
        }
        // The plant player never sees a seed-selection menu -- the other player would be waiting.
        assertFalse(mode.requiresSeedSelection(session));
        for (String plant : mode.preSelectedPlants()) {
            assertTrue(session.isSeedSelected(plant), plant + " should already be in the loadout");
        }
        assertFalse(mode.getRoster().isEmpty(), "the zombie player needs something to buy");
    }

    // --- Two sun pools ----------------------------------------------------------------------------

    @Test
    void plantingAndSummoningSpendFromDifferentBanks() {
        GameSession session = openPlay();
        VersusIZombieMode mode = modeOf(session);

        int plantSunBefore = session.getSunAmount();
        int zombieSunBefore = mode.getZombieSun();

        Result planted = session.plant(0, 0, "Sunflower");
        assertTrue(planted.success(), planted.message());
        assertTrue(session.getSunAmount() < plantSunBefore, "a plant costs the PLANT player sun");
        assertEquals(zombieSunBefore, mode.getZombieSun(),
                "planting must not touch the zombie player's bank");

        int plantSunAfterPlanting = session.getSunAmount();
        Result summoned = session.summonZombie("ZombieImp", 7, 0);
        assertTrue(summoned.success(), summoned.message());
        assertTrue(mode.getZombieSun() < zombieSunBefore, "a summon costs the ZOMBIE player sun");
        assertEquals(plantSunAfterPlanting, session.getSunAmount(),
                "summoning must not touch the plant player's bank");
    }

    @Test
    void aBrokeZombiePlayerCannotSummonEvenWhenThePlantPlayerIsRich() {
        GameSession session = openPlay();
        VersusIZombieMode mode = modeOf(session);

        // Drain the zombie bank the only way there is: buy until it will not stretch to another Imp.
        while (mode.getZombieSun() >= cheapest(mode)) {
            assertTrue(session.summonZombie("ZombieImp", 8, 0).success());
        }
        session.increaseSunAmount(10_000);   // the plant player is flush

        Result refused = session.summonZombie("ZombieImp", 8, 0);
        assertFalse(refused.success(),
                "a shared bank would let the zombie player spend the plant player's sun");
    }

    // --- The plant player's half of the lawn ------------------------------------------------------

    @Test
    void thePlantPlayerCannotPlantPastTheRedLine() {
        GameSession session = started();
        VersusIZombieMode mode = modeOf(session);
        session.increaseSunAmount(10_000);

        Result blocked = session.plant(mode.getRedLineColumn(), 2, "Wall-nut");
        assertFalse(blocked.success(), "walling off the spawn area is an unloseable position");
        assertTrue(blocked.message().contains("red line"), "the refusal has to say why: "
                + blocked.message());

        assertTrue(session.plant(mode.getRedLineColumn() - 1, 2, "Wall-nut").success(),
                "the column just left of the line is still theirs");
    }

    @Test
    void theZombiePlayerCannotSummonLeftOfTheRedLine() {
        GameSession session = openPlay();
        VersusIZombieMode mode = modeOf(session);
        assertFalse(session.summonZombie("ZombieImp", mode.getRedLineColumn() - 1, 0).success());
        assertTrue(session.summonZombie("ZombieImp", mode.getRedLineColumn(), 0).success());
    }

    // --- The four ways a match ends ---------------------------------------------------------------

    @Test
    void nobodyHasWonWhileTheMatchIsStillBeingPlayed() {
        GameSession session = started();
        VersusIZombieMode mode = modeOf(session);
        session.evaluateModeRules();

        assertNull(mode.winner());
        assertNull(mode.ending());
        assertEquals(GameState.PLAYING, session.getState());
    }

    @Test
    void eatingEveryBrainIsAZombieWin() {
        GameSession session = started();
        VersusIZombieMode mode = modeOf(session);

        // One zombie already at the house in every lane. Placed directly rather than summoned: this is
        // about the outcome, not about the nine columns of walking that lead to it.
        for (int lane = 0; lane < mode.brainsTotal(); lane++) {
            session.getMap().getRow(lane).getZombies()
                    .add(ZombieFactory.createZombie("ZombieDefault", 0, lane, session));
        }
        session.evaluateModeRules();

        assertEquals(Faction.ZOMBIES, mode.winner());
        assertEquals(VersusIZombieMode.Ending.BRAINS_EATEN, mode.ending());
        assertEquals(mode.brainsTotal(), mode.brainsEaten());
        // The inversion: a zombie victory is the session's WON, because this mode is written from the
        // zombie player's seat exactly as single-player I, Zombie is.
        assertTrue(mode.checkWin(session));
        assertFalse(mode.checkLose(session));
        assertEquals(GameState.WON, session.getState());
    }

    @Test
    void runningOutTheClockWithABrainStandingIsAPlantWin() {
        GameSession session = started(5);
        VersusIZombieMode mode = modeOf(session);

        session.advanceTime(6);
        session.evaluateModeRules();

        assertEquals(Faction.PLANTS, mode.winner());
        assertEquals(VersusIZombieMode.Ending.TIME_UP, mode.ending());
        assertTrue(mode.checkLose(session));
        assertFalse(mode.checkWin(session));
        assertEquals(GameState.LOST, session.getState(),
                "a plant win is the session's LOST -- winner() is what the players are told");
    }

    @Test
    void aSpentHordeIsAPlantWin() {
        GameSession session = openPlay();
        VersusIZombieMode mode = modeOf(session);

        while (mode.getZombieSun() >= cheapest(mode)) {
            session.summonZombie("ZombieImp", 8, 0);
        }
        // Every summoned zombie shot down. The sun makers are left standing on purpose: they never
        // advance and never eat, so a rule that counted them could not fire while one was alive.
        for (Row row : session.getMap().getRows()) {
            row.getZombies().removeIf(zombie -> !mode.isSunProducer(zombie));
        }
        session.evaluateModeRules();

        assertEquals(Faction.PLANTS, mode.winner());
        assertEquals(VersusIZombieMode.Ending.HORDE_SPENT, mode.ending());
    }

    @Test
    void theOutcomeIsDecidedOnceAndDoesNotFlipAfterwards() {
        GameSession session = started(5);
        VersusIZombieMode mode = modeOf(session);
        session.advanceTime(6);
        session.evaluateModeRules();
        assertEquals(Faction.PLANTS, mode.winner());

        // Brains eaten AFTER the clock ran out must not retroactively hand the match to the zombies.
        for (int lane = 0; lane < mode.brainsTotal(); lane++) {
            session.getMap().getRow(lane).getZombies()
                    .add(ZombieFactory.createZombie("ZombieDefault", 0, lane, session));
        }
        session.evaluateModeRules();
        assertEquals(Faction.PLANTS, mode.winner());
        assertEquals(VersusIZombieMode.Ending.TIME_UP, mode.ending());
    }

    // --- Wiring the views and the campaign read ----------------------------------------------------

    @Test
    void aVersusMatchIsABrainLawnButNotAMiniGameClear() {
        GameSession session = started();
        assertTrue(session.getMode() instanceof BrainLawn,
                "the brains, the red line and the roster panel all key off this");
        assertFalse(session.getMode() instanceof IZombieMode,
                "extending IZombieMode would file a versus win as a single-player mini-game clear");
        assertFalse(session.getMode().countsTowardQuests());
    }

    @Test
    void theClockCountsDownFromTheMatchLength() {
        GameSession session = started(100);
        VersusIZombieMode mode = modeOf(session);
        assertEquals(100, mode.ticksRemaining(session));
        session.advanceTime(40);
        assertEquals(60, mode.ticksRemaining(session));
        session.advanceTime(1000);
        assertEquals(0, mode.ticksRemaining(session), "it stops at zero rather than going negative");
    }

    @Test
    void theSunMakersPayTheZombiePlayerAndLeaveNothingOnTheLawn() {
        GameSession session = started();
        VersusIZombieMode mode = modeOf(session);
        int before = mode.getZombieSun();

        for (int tick = 0; tick < 25 * Constants.TICKS_PER_SECOND; tick++) {
            session.advanceTime(1);
            mode.onTick(session);
        }

        assertTrue(mode.getZombieSun() > before, "the makers are the zombie player's whole economy");
        assertTrue(session.getActiveSuns().isEmpty(),
                "a sun on the lawn belongs to the plant player; the maker's income must not land there");
    }

    @Test
    void onStartIsIdempotent() {
        // The server calls GameEngine.init() and both clients build the same level; a second onStart
        // must not reset the banks or stack a second row of sun makers.
        GameSession session = openPlay();
        VersusIZombieMode mode = modeOf(session);
        session.summonZombie("ZombieImp", 8, 0);
        int sun = mode.getZombieSun();
        int zombies = session.getMap().getRow(0).getZombies().size();

        session.startMode();

        assertEquals(sun, mode.getZombieSun());
        assertEquals(zombies, session.getMap().getRow(0).getZombies().size());
    }

    // --- Balance, at the only point it is decidable in a unit test ---------------------------------
    //
    // These do not pin the numbers -- the price table is a knob and pinning it would just mean editing
    // two files instead of one. They pin the two SHAPES the first build got wrong: an opening the plant
    // player could not answer, and a belt where the cheap entry was strictly the best entry.

    @Test
    void theOpeningBankCannotPutAZombieInEveryLane() {
        VersusIZombieMode mode = modeOf(started());
        assertTrue(mode.startingZombieSun() < mode.brainsTotal() * cheapest(mode),
                "five lanes bought on tick one is not a match, it is a countdown: the plant player can "
                        + "afford to answer in one of them");
    }

    @Test
    void thePlantPlayerCanOpenWithAnEngineAndAShooter() {
        VersusIZombieMode mode = modeOf(started());
        int sunflowers = 3;
        int engine = sunflowers * costOf("Sunflower") + costOf("Peashooter");
        assertTrue(mode.startingPlantSun() >= engine,
                "the opening bank has to cover " + sunflowers + " Sunflowers and something that shoots ("
                        + engine + "); it was " + mode.startingPlantSun());
    }

    @Test
    void theHeaviestZombieIsAMatchLongCommitment() {
        // The Gargantuar has nineteen times a basic's health, and no five columns of shooters bring one
        // down before it reaches the brain. It was priced at six times a basic and affordable from the
        // opening bank, so the answer to a Gargantuar was another Gargantuar. It has to be something a
        // player saves for, not something they start with.
        VersusIZombieMode mode = modeOf(started());
        int dearest = mode.getRoster().values().stream().max(Integer::compareTo).orElseThrow();
        assertTrue(dearest > mode.startingZombieSun(),
                "the toughest thing on the belt must not be an opening move");
    }

    @Test
    void aSunMakerCanActuallyBeShotDownInsideAMatch() {
        // The plant player's only answer to the zombie economy is to destroy it, and that has to be
        // reachable. A buckethead maker is 1290 health; a Peashooter does 20 damage every 1.5 seconds,
        // so killing one took ninety-seven seconds of clear fire out of a hundred-and-eighty second
        // match -- counterplay that existed on paper and never once in a game.
        GameSession session = started();
        VersusIZombieMode mode = modeOf(session);
        Zombie maker = session.getMap().getRow(0).getZombies().stream()
                .filter(mode::isSunProducer).findFirst().orElseThrow();

        double shooterDps = peashooterDamage() / peashooterInterval();
        double secondsToKill = maker.getHealth().getMaxTotalHp() / shooterDps;
        assertTrue(secondsToKill < mode.matchDurationTicks() / (double) Constants.TICKS_PER_SECOND / 3,
                "one Peashooter needs " + Math.round(secondsToKill) + "s to bring a sun maker down, "
                        + "and it has to be worth committing a lane to");
    }

    private static int costOf(String plant) {
        return utils.registry.PlantRegistry.getInstance().getTemplateByName(plant).getCost();
    }

    private static double peashooterDamage() {
        return utils.registry.PlantRegistry.getInstance().getTemplateByName("Peashooter").getDamage();
    }

    private static double peashooterInterval() {
        return utils.registry.PlantRegistry.getInstance()
                .getTemplateByName("Peashooter").getActionInterval();
    }

    // --- The opening grace ------------------------------------------------------------------------

    @Test
    void nothingIsSummonableWhileThePlantPlayerIsStillDigging() {
        GameSession session = started();
        VersusIZombieMode mode = modeOf(session);
        assertTrue(mode.graceTicks() > 0, "there is no match without it: see the class comment");
        assertTrue(mode.summoningLocked(session));

        Result tooSoon = session.summonZombie("ZombieDefault", 8, 0);
        assertFalse(tooSoon.success());
        assertTrue(tooSoon.message().toLowerCase().contains("diggin"),
                "the refusal has to say WHY, and how long is left: " + tooSoon.message());
        assertTrue(session.getMap().getRow(0).getZombies().stream().allMatch(mode::isSunProducer),
                "a refused summon must not put a zombie on the lawn anyway");

        session.advanceTime(mode.graceTicks());
        assertFalse(mode.summoningLocked(session));
        assertTrue(session.summonZombie("ZombieDefault", 8, 0).success(),
                "and the belt opens the moment it expires");
    }

    @Test
    void aBrokenOpeningIsNotASpentHorde() {
        // The grace period looks EXACTLY like a spent horde from hordeSpent's seat -- an empty lawn and
        // a bank the player has not been allowed to spend -- so a mode that did not exclude it would
        // hand the plant player the match on tick one, and the log would say the zombies ran out.
        GameSession session = started();
        VersusIZombieMode mode = modeOf(session);
        for (Row row : session.getMap().getRows()) {
            row.getZombies().removeIf(zombie -> !mode.isSunProducer(zombie));
        }
        session.evaluateModeRules();
        assertNull(mode.winner());
    }

    @Test
    void theGraceNeverSwallowsAShortMatch() {
        // The server can shorten a match and the harness does. A fixed twenty seconds would be the whole
        // of a ten-second one, and the zombie player would never get a turn.
        GameSession session = started(40);
        VersusIZombieMode mode = modeOf(session);
        assertTrue(mode.graceTicks() <= 40 / 4,
                "the grace is capped at a quarter of the match, not a constant");
        session.advanceTime(mode.graceTicks());
        assertFalse(mode.summoningLocked(session));
    }

    @Test
    void anUnknownZombieIsRefusedByName() {
        GameSession session = openPlay();
        Result refused = session.summonZombie("ZombieDrHeadInAJar", 7, 0);
        assertFalse(refused.success());
        assertNotNull(refused.message());
        assertFalse(modeOf(session).isSummonable("ZombieDrHeadInAJar"));
        assertTrue(modeOf(session).isSummonable("zombieimp"), "case must not decide a purchase");
    }
}
