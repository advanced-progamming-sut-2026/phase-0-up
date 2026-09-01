package models.game.gamemodes;

import models.entities.projectiles.Element;
import models.entities.zombies.BossKind;
import models.entities.zombies.Zombie;
import models.entities.zombies.Zomboss;
import models.game.GameSession;
import models.game.Level;
import models.map.Row;
import models.user.Profile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.Constants;
import utils.gameinitializers.GameInitializer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The boss fight's rules, and specifically the ones that are invisible when they break.
//
// Almost everything here is a two-row bookkeeping question, because that is where this mode's real
// complexity is. A Zomboss is a member of BOTH of the rows it straddles -- that membership is the
// entire mechanism behind "plants in both rows can shoot it" -- and a boss filed into one row, or
// left behind in a row it has moved out of, still LOOKS completely correct on screen. It is drawn from
// its top row either way, it still takes damage, it still attacks. The only symptom is that half the
// player's defence quietly stops being able to hit it.
class ZombossModeTest {

    @BeforeAll
    static void loadGameData() {
        new GameInitializer().loadAllData();
    }

    // A seeded mode, so which attacks come out and which rows the machine picks are reproducible.
    private static GameSession started(BossKind kind) {
        ZombossMode mode = new ZombossMode(kind, List.of("ZombieDefault"), new Random(7));
        Level level = new Level(new models.game.Wave[0], null, mode, 0,
                new ArrayList<>(List.of("Peashooter", "Wall-nut")), 0,
                Constants.DEFAULT_SEED_SLOTS, null);
        GameSession session = new GameSession(new Profile(), level);
        session.startMode();
        return session;
    }

    private static ZombossMode modeOf(GameSession session) {
        return (ZombossMode) session.getMode();
    }

    // Ticks the clock AND the mode together, which is what the engine does. session.advanceTime alone
    // moves only the clock, and every one of this mode's four timers lives in onTick.
    private static void run(GameSession session, int ticks) {
        for (int i = 0; i < ticks; i++) {
            session.tick();
            session.getMode().onTick(session);
        }
    }

    // Which rows currently hold the boss, read from the ROW LISTS rather than from the boss's own
    // position -- the two are what has to agree, and asking the boss would only ever confirm the boss.
    private static Set<Integer> rowsHolding(GameSession session, Zombie boss) {
        Set<Integer> rows = new HashSet<>();
        for (Row row : session.getMap().getRows()) {
            if (row.getZombies().contains(boss)) {
                rows.add(row.getIndex());
            }
        }
        return rows;
    }

    // --- Setup ------------------------------------------------------------------------------------

    @Test
    void theBossArrivesStandingInTwoRows() {
        GameSession session = started(BossKind.SPHINX);
        Zomboss boss = modeOf(session).getBoss();
        assertNotNull(boss, "onStart must put a boss on the lawn");
        assertEquals(2, boss.rowSpan());
        assertEquals(2, boss.occupiedRows().size());
        assertEquals(rowsHolding(session, boss), new HashSet<>(boss.occupiedRows()),
                "the rows holding the boss must be exactly the rows it says it occupies");
    }

    // The rule the whole two-row design exists for. Row membership is how every plant, pea and splash
    // in this codebase answers "what is in this lane", so a boss in one list is a boss half the lawn
    // cannot touch -- and nothing about the screen would say so.
    @Test
    void bothOfItsRowsCanReachIt() {
        GameSession session = started(BossKind.DRAGON);
        Zomboss boss = modeOf(session).getBoss();
        for (int row : boss.occupiedRows()) {
            assertTrue(session.getMap().getRow(row).getZombies().contains(boss),
                    "row " + row + " cannot shoot a boss it is not holding");
        }
    }

    @Test
    void aBossLevelPicksNoSeedsAndDropsNoSun() {
        GameSession session = started(BossKind.SHARK);
        assertFalse(session.getMode().requiresSeedSelection(session));
        assertFalse(session.getMode().allowsSkySun());
        assertTrue(session.getMode().managesPlantInventory());
    }

    // --- Shifting rows ----------------------------------------------------------------------------

    // A shift has to move BOTH halves of the boss's position: the lane on its movement component and
    // the two row lists holding it. Leaving it in a row it has walked out of is the failure that would
    // have plants shooting empty ground, and it would look perfectly fine.
    @Test
    void shiftingRowsLeavesItInExactlyTwoRows() {
        GameSession session = started(BossKind.SPHINX);
        Zomboss boss = modeOf(session).getBoss();
        Set<Integer> topRowsSeen = new HashSet<>();
        int ticks = ZombossMode.openingGraceTicks() + ZombossMode.shiftIntervalTicks() * 4;
        for (int i = 0; i < ticks; i++) {
            session.tick();
            session.getMode().onTick(session);
            topRowsSeen.add(boss.getMovement().getPositionY());
            assertEquals(new HashSet<>(boss.occupiedRows()), rowsHolding(session, boss),
                    "after a shift the row lists must match the boss's own rows, tick " + i);
        }
        assertTrue(topRowsSeen.size() > 1, "a Sphinx is supposed to move between rows");
    }

    // The spec's one explicit exception, and the asset dump agrees with it: the Tuskmaster's animation
    // is the only one of the four with no walk clips and no summon clip.
    @Test
    void theMammothNeitherMovesNorSummons() {
        GameSession session = started(BossKind.MAMMOTH);
        Zomboss boss = modeOf(session).getBoss();
        int startRow = boss.getMovement().getPositionY();
        run(session, ZombossMode.openingGraceTicks() + ZombossMode.shiftIntervalTicks() * 4);
        assertEquals(startRow, boss.getMovement().getPositionY(),
                "the Mammoth stands still");
        assertFalse(BossKind.MAMMOTH.spawnsZombies());
        assertFalse(BossKind.MAMMOTH.shiftsRows());
    }

    // --- Health bands -----------------------------------------------------------------------------

    // Three bands, staggering the machine twice. Twice and not three times: the last boundary is the
    // boss dying, and a corpse reeling from a stun it never comes out of is the level looking hung.
    @Test
    void eachBandBoundaryStaggersTheMachineExactlyOnce() {
        GameSession session = started(BossKind.DRAGON);
        Zomboss boss = modeOf(session).getBoss();
        int max = boss.getHealth().getMaxTotalHp();
        int band = max / Zomboss.SECTIONS;

        assertFalse(boss.crossedSectionBoundary(), "a boss at full health has crossed nothing");

        boss.getHealth().applyDamage(band + 1, Element.NEUTRAL, null);
        assertTrue(boss.crossedSectionBoundary(), "the first band emptying must stagger it");
        assertFalse(boss.crossedSectionBoundary(), "and must not stagger it again on the next tick");
        assertEquals(2, boss.sectionsRemaining());

        boss.getHealth().applyDamage(band, Element.NEUTRAL, null);
        assertTrue(boss.crossedSectionBoundary(), "the second band emptying must stagger it too");
        assertEquals(1, boss.sectionsRemaining());

        boss.getHealth().applyDamage(max, Element.NEUTRAL, null);
        assertTrue(boss.getHealth().isDead());
        assertFalse(boss.crossedSectionBoundary(), "dying is not a stagger");
    }

    @Test
    void theLevelIsWonWhenTheMachineFalls() {
        GameSession session = started(BossKind.SHARK);
        ZombossMode mode = modeOf(session);
        assertFalse(mode.checkWin(session), "the fight is not over while the boss is standing");
        mode.getBoss().getHealth().applyDamage(mode.getBoss().getHealth().getMaxTotalHp(),
                Element.NEUTRAL, null);
        assertTrue(mode.checkWin(session));
    }

    // --- The conveyor -----------------------------------------------------------------------------

    @Test
    void theBeltDeliversAndSpendsOnePlantAtATime() {
        GameSession session = started(BossKind.SPHINX);
        ZombossMode mode = modeOf(session);
        int opening = mode.getConveyor().size();
        assertTrue(opening > 0, "the belt must not start empty -- the player has nothing to plant");

        run(session, ZombossMode.openingGraceTicks());
        assertTrue(mode.getConveyor().size() > opening, "the belt must keep delivering");

        String held = mode.getConveyor().get(0);
        int before = mode.getConveyor().size();
        assertTrue(mode.hasPlantAvailable(held));
        mode.consumePlant(held);
        assertEquals(before - 1, mode.getConveyor().size(),
                "planting spends exactly one card, not every copy of that plant");
    }

    // The belt only ever carries what the level authored, so a boss level is written exactly like the
    // four days before it and the belt hands out what the player would otherwise have picked.
    @Test
    void theBeltOnlyCarriesTheLevelsOwnPlants() {
        GameSession session = started(BossKind.DRAGON);
        run(session, ZombossMode.openingGraceTicks() * 2);
        for (String plant : modeOf(session).getConveyor()) {
            assertTrue(plant.equals("Peashooter") || plant.equals("Wall-nut"),
                    "the belt delivered \"" + plant + "\", which this level never listed");
        }
    }

    @Test
    void everySeasonFieldsItsOwnBoss() {
        for (models.game.EnvironmentType season : models.game.EnvironmentType.values()) {
            BossKind kind = BossKind.forSeason(season);
            assertEquals(season, kind.getSeason(),
                    season + " must field the boss that belongs to it");
            assertFalse(kind.getAttacks().isEmpty(), kind + " has nothing to attack with");
            assertEquals(kind, BossKind.forAlias(kind.getAlias()),
                    "a boss must be findable by the alias it fights under -- SpriteRegistry keys on it");
        }
    }
}
