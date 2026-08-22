package controllers.systems.game;

import factories.LevelFactory;
import models.game.EnvironmentType;
import models.game.GameSession;
import models.game.Level;
import models.game.Wave;
import models.templates.ZombieTemplate;
import models.user.Profile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.Constants;
import utils.Result;
import utils.gameinitializers.GameInitializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The two balance rules that are easy to break and impossible to see in a screenshot: zombies must
// arrive one at a time with a randomised gap, and a wave's roster must lean on cheap zombies early and
// dear ones late.
//
// Everything here drives the real WaveSystem against real level data with a SEEDED Random, so a failure
// is reproducible rather than a flake.
class WaveBalanceTest {

    // Ancient Egypt day 1: four waves, the shortest curve the game ships, so wave 1 is progress 0 and
    // wave 4 is progress 1 exactly.
    private static final String LEVEL_ID = "s1l1";

    @BeforeAll
    static void loadGameData() {
        new GameInitializer().loadAllData();
    }

    private static GameSession session(int difficulty) {
        Profile profile = new Profile();
        profile.setDifficultyLevel(difficulty);
        Level level = LevelFactory.createLevel(LEVEL_ID);
        return new GameSession(profile, level);
    }

    // Runs the system for `ticks` and records the tick each zombie walked on at.
    private static List<Long> spawnTicks(GameSession gameSession, WaveSystem waves, int ticks) {
        List<Long> arrivals = new ArrayList<>();
        for (long tick = 1; tick <= ticks; tick++) {
            for (Result event : waves.processTick(gameSession, tick)) {
                if (event.message().startsWith("Zombie ")) {
                    arrivals.add(tick);
                }
            }
        }
        return arrivals;
    }

    // --- 1. staggered spawning --------------------------------------------------------------------

    @Test
    void zombiesArriveOneAtATime() {
        GameSession gameSession = session(Constants.DEFAULT_DIFFICULTY_LEVEL);
        List<Long> arrivals = spawnTicks(gameSession, new WaveSystem(new Random(7)), 900);

        assertFalse(arrivals.isEmpty(), "wave 1 should have put zombies on the lawn within 90 seconds");
        for (int i = 1; i < arrivals.size(); i++) {
            assertTrue(arrivals.get(i) > arrivals.get(i - 1),
                    "two zombies arrived on the same tick -- the wave is being dumped, not trickled");
        }
    }

    @Test
    void gapsBetweenZombiesStayInsideTheRolledWindow() {
        GameSession gameSession = session(Constants.DEFAULT_DIFFICULTY_LEVEL);
        List<Long> arrivals = spawnTicks(gameSession, new WaveSystem(new Random(11)), 1500);

        int compared = 0;
        for (int i = 1; i < arrivals.size(); i++) {
            long gap = arrivals.get(i) - arrivals.get(i - 1);
            // A gap far larger than the window means a new WAVE started, which is governed by the 75%
            // health rule rather than by this counter; those are not intra-wave gaps and are skipped.
            if (gap > Constants.ZOMBIE_SPAWN_DELAY_MAX_TICKS) {
                continue;
            }
            compared++;
            assertTrue(gap >= Constants.ZOMBIE_SPAWN_DELAY_MIN_TICKS,
                    "zombies " + (i - 1) + " and " + i + " were " + gap + " ticks apart, under the "
                            + Constants.ZOMBIE_SPAWN_DELAY_MIN_TICKS + "-tick floor");
        }
        assertTrue(compared >= 3, "expected several intra-wave gaps to check, saw " + compared);
    }

    @Test
    void theGapIsActuallyRandomisedRatherThanFixed() {
        GameSession gameSession = session(Constants.DEFAULT_DIFFICULTY_LEVEL);
        List<Long> arrivals = spawnTicks(gameSession, new WaveSystem(new Random(3)), 1500);

        java.util.Set<Long> distinctGaps = new java.util.HashSet<>();
        for (int i = 1; i < arrivals.size(); i++) {
            long gap = arrivals.get(i) - arrivals.get(i - 1);
            if (gap <= Constants.ZOMBIE_SPAWN_DELAY_MAX_TICKS) {
                distinctGaps.add(gap);
            }
        }
        assertTrue(distinctGaps.size() > 1,
                "every intra-wave gap was identical (" + distinctGaps + ") -- the delay is still fixed");
    }

    @Test
    void higherDifficultyBringsZombiesInFaster() {
        long easy = medianGap(session(1), new Random(5));
        long normal = medianGap(session(Constants.DEFAULT_DIFFICULTY_LEVEL), new Random(5));
        long hard = medianGap(session(5), new Random(5));

        assertTrue(easy > normal, "difficulty 1 should space zombies out more than the default; "
                + easy + " vs " + normal);
        assertTrue(hard < normal, "difficulty 5 should tighten the stream; " + hard + " vs " + normal);
    }

    private static long medianGap(GameSession gameSession, Random random) {
        List<Long> arrivals = spawnTicks(gameSession, new WaveSystem(random), 3000);
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < arrivals.size(); i++) {
            long gap = arrivals.get(i) - arrivals.get(i - 1);
            // Same filter as above: skip the between-wave jump, which this counter does not control.
            if (gap <= (long) Constants.ZOMBIE_SPAWN_DELAY_MAX_TICKS * Constants.DEFAULT_DIFFICULTY_LEVEL) {
                gaps.add(gap);
            }
        }
        java.util.Collections.sort(gaps);
        return gaps.isEmpty() ? 0 : gaps.get(gaps.size() / 2);
    }

    // --- 2. zombie variety ------------------------------------------------------------------------

    @Test
    void earlyWavesNeverFieldElites() {
        // The gate, not the weighting, is what guarantees this: an elite carries a non-zero weight at
        // every progress, so if it were in the roster at all it would eventually be drawn.
        List<String> opening = ZombiePool.rosterFor(EnvironmentType.ANCIENT_EGYPT, 0d);
        assertFalse(opening.contains("ZombieGargantuar"),
                "wave 1 must not be able to field a Gargantuar at all: " + opening);
        assertTrue(drawCounts(0d, 400).keySet().stream()
                        .noneMatch(alias -> alias.equals("ZombieGargantuar")),
                "an elite was drawn in an opening wave");
    }

    @Test
    void earlyWavesAreCheaperPerZombieThanLateWaves() {
        double early = meanCost(0d);
        double middle = meanCost(0.5d);
        double late = meanCost(1d);

        assertTrue(early < middle, "wave 1 should field cheaper zombies than mid-level; "
                + early + " vs " + middle);
        assertTrue(middle < late, "mid-level should field cheaper zombies than the finale; "
                + middle + " vs " + late);
    }

    @Test
    void lateWavesFieldAMixRatherThanOneType() {
        Map<String, Integer> late = drawCounts(1d, 400);
        assertTrue(late.size() >= 4, "a late wave should field a mix: " + late);
        assertTrue(late.containsKey("ZombieGargantuar"), "the finale should reach for elites: " + late);
        // A finale of nothing but elites is a wall rather than a wave, so the floor tier must survive.
        assertTrue(late.containsKey("ZombieDefault"),
                "the finale should still field Browncoats between the big ones: " + late);
    }

    @Test
    void everyChapterOffersMoreTypesLateThanEarly() {
        for (EnvironmentType environment : EnvironmentType.values()) {
            int early = ZombiePool.rosterFor(environment, 0d).size();
            int late = ZombiePool.rosterFor(environment, 1d).size();
            assertTrue(late > early,
                    environment + " offers " + late + " types at the end and " + early
                            + " at the start -- the curve is flat");
        }
    }

    @Test
    void theAuthoredRosterIsNeverLost() {
        List<String> authored = List.of("ZombieDefault", "ZombieBeachOctopus");
        List<String> resolved = ZombiePool.resolveAliases(authored, EnvironmentType.BIG_WAVE_BEACH, 0d);
        assertTrue(resolved.containsAll(authored),
                "a level's own roster must survive the union with the chapter pool: " + resolved);
    }

    // Draws `draws` picks the way a wave actually would at this progress: the ROSTER is gated by
    // progress and the pick is then weighted by the same progress. Feeding the late roster at an early
    // progress -- which the first draft of this test did -- measures a combination the game can never
    // produce, and the gate is half the mechanism.
    private static Map<String, Integer> drawCounts(double progress, int draws) {
        List<ZombieTemplate> pool = ZombiePool.resolveTemplates(
                ZombiePool.rosterFor(EnvironmentType.ANCIENT_EGYPT, progress));
        Random random = new Random(99);
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < draws; i++) {
            ZombieTemplate pick = ZombiePool.pick(pool, pool, progress, random);
            counts.merge(pick.getAlias(), 1, Integer::sum);
        }
        return counts;
    }

    // Average wave-point cost of a zombie drawn at this progress -- the single number that says whether
    // the difficulty curve exists.
    private static double meanCost(double progress) {
        Map<String, Integer> counts = drawCounts(progress, 400);
        int total = 0;
        int spent = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            ZombieTemplate template = utils.registry.ZombieRegistry.getInstance()
                    .getZombieTemplateByAlias(entry.getKey());
            spent += template.getWavePointCost() * entry.getValue();
            total += entry.getValue();
        }
        return total == 0 ? 0d : spent / (double) total;
    }

    // --- 3. the budget invariant still holds ------------------------------------------------------

    @Test
    void aWaveStillSpendsItsWholeBudget() {
        GameSession gameSession = session(Constants.DEFAULT_DIFFICULTY_LEVEL);
        WaveSystem waves = new WaveSystem(new Random(21));
        spawnTicks(gameSession, waves, 400);

        Wave wave = waves.getActiveWave();
        assertTrue(wave != null, "wave 1 never launched");
        int spent = wave.getZombies().stream().mapToInt(z -> z.getWavePointCost()).sum();
        assertEquals(wave.getWaveCost(), spent,
                "the bought zombies must sum to the wave's budget exactly");
    }
}
