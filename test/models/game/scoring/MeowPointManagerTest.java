package models.game.scoring;

import models.game.gamemodes.ScoringMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Unit tests for the scoring game's rulebook. Deliberately touch no board and no registry: the parts
// exercised here are pure arithmetic and date maths, so they run in milliseconds and cannot break
// because of unrelated game data.
class MeowPointManagerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 21);

    // --- Daily determinism ------------------------------------------------------------------------

    @Test
    void sameDayProducesSameSeedForEveryPlayer() {
        assertEquals(ScoringMode.dailySeed(DAY), ScoringMode.dailySeed(DAY),
                "two players opening the game on the same date must get the same lawn");
    }

    @Test
    void differentDayProducesDifferentSeed() {
        assertNotEquals(ScoringMode.dailySeed(DAY), ScoringMode.dailySeed(DAY.plusDays(1)),
                "the lawn must change at midnight");
    }

    @Test
    void seedIsStableAcrossModeInstances() {
        assertEquals(new ScoringMode(DAY).getSeed(), new ScoringMode(DAY).getSeed());
    }

    // --- Rule values ------------------------------------------------------------------------------

    @Test
    void ruleAwardsMatchTheSpec() {
        assertEquals(50, MeowPointRule.SIMULTANEOUS_KILL.getAward());
        assertEquals(30, MeowPointRule.SPEED_KILL.getAward());
        assertEquals(20, MeowPointRule.ONE_SHOT.getAward());
        assertEquals(1, MeowPointRule.SUN_HOARDER.getAward());
        assertEquals(100, MeowPointRule.FLAWLESS_DEFENSE.getAward());
    }

    // --- Scorecard --------------------------------------------------------------------------------

    @Test
    void freshScorecardStartsEmpty() {
        MeowPointManager m = new MeowPointManager();
        assertEquals(0, m.getTotal());
        assertEquals(0, m.getKills());
        assertTrue(m.buildScorecard().contains("No bonuses earned"),
                "a run that scored nothing should say so rather than print a bare zero");
    }

    @Test
    void scorecardListsOnlyRulesThatActuallyFired() {
        MeowPointManager m = new MeowPointManager();
        String card = m.buildScorecard();
        assertTrue(card.contains("MEOW POINTS"), "card is titled in Meow Points");
        assertTrue(card.contains("TOTAL"));
        for (MeowPointRule rule : MeowPointRule.values()) {
            assertTrue(!card.contains(rule.getLabel()),
                    "an unearned rule (" + rule.getLabel() + ") must not clutter the card");
        }
    }
}
