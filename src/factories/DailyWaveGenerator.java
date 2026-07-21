package factories;

import models.game.Wave;
import models.templates.ZombieTemplate;
import utils.registry.ZombieRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// Builds the scoring game's wave sequence for one day.
//
// Determinism is the whole point: given the same seed this returns byte-identical waves -- same count,
// same budgets, same delays, same allowed zombie pools, in the same order. Combined with a WaveSystem
// seeded from that same number (which is what picks the individual zombies and their lanes), two
// players opening the scoring game on the same date fight exactly the same assault.
//
// Nothing here reads the wall clock or any per-player state; the seed is the only input, so the output
// cannot drift between machines.
public final class DailyWaveGenerator {

    private static final int MIN_WAVES = 6;
    private static final int EXTRA_WAVE_SPREAD = 3;       // 6..8 waves
    // Budgets are expressed as multiples of the cheapest zombie in the registry rather than as raw
    // numbers, because wave-point costs live in the data (currently 100..1500). A hard-coded budget
    // smaller than the cheapest zombie buys NOTHING and the wave walks on empty -- which is exactly
    // what a flat 60 did here.
    private static final int BASE_BUDGET_UNITS = 3;       // opening wave affords ~3 cheap zombies
    private static final int BUDGET_GROWTH_UNITS = 2;     // +2 zombies' worth per wave
    private static final int FINAL_WAVE_MULTIPLIER = 2;   // the flag wave is twice the previous budget
    private static final int BASE_DELAY_SECONDS = 25;
    private static final int DELAY_JITTER_SECONDS = 10;
    private static final int OPENING_DELAY_SECONDS = 40;  // build time before anything walks on
    private static final int POOL_MIN = 3;
    private static final int POOL_SPREAD = 3;             // 3..5 zombie types per wave

    private DailyWaveGenerator() { }

    public static Wave[] generate(long seed) {
        Random rng = new Random(seed);
        List<String> catalogue = zombieCatalogue();

        int waveCount = MIN_WAVES + rng.nextInt(EXTRA_WAVE_SPREAD);
        List<Wave> waves = new ArrayList<>(waveCount);

        int unit = cheapestWaveCost();
        int previousBudget = unit * BASE_BUDGET_UNITS;
        for (int i = 0; i < waveCount; i++) {
            boolean isFinal = i == waveCount - 1;
            int number = i + 1;

            int budget = isFinal
                    ? previousBudget * FINAL_WAVE_MULTIPLIER
                    : unit * (BASE_BUDGET_UNITS + i * BUDGET_GROWTH_UNITS) + rng.nextInt(unit);
            previousBudget = budget;

            int delaySeconds = i == 0
                    ? OPENING_DELAY_SECONDS
                    : BASE_DELAY_SECONDS + rng.nextInt(DELAY_JITTER_SECONDS);

            // Wave.getDelay() is in SECONDS -- WaveSystem converts it to ticks itself (and scales it by
            // difficulty). Handing it ticks here made every gap ten times too long and no wave ever ran.
            waves.add(new Wave(number, isFinal, budget, delaySeconds, poolFor(catalogue, rng)));
        }
        return waves.toArray(new Wave[0]);
    }

    // The zombie types one wave is allowed to spend its budget on. Drawn from a shuffled copy so a
    // wave never lists the same type twice, and kept small so each wave has a recognisable character.
    private static List<String> poolFor(List<String> catalogue, Random rng) {
        if (catalogue.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> shuffled = new ArrayList<>(catalogue);
        Collections.shuffle(shuffled, rng);
        int size = Math.min(shuffled.size(), POOL_MIN + rng.nextInt(POOL_SPREAD));
        return new ArrayList<>(shuffled.subList(0, size));
    }

    // Every registered zombie, sorted by alias. The sort matters: the registry is a HashMap, whose
    // iteration order is not guaranteed across runs or JVMs, and an unstable order would quietly break
    // the "same lawn for everyone" promise even with an identical seed.
    private static List<String> zombieCatalogue() {
        List<String> aliases =
                new ArrayList<>(ZombieRegistry.getInstance().getZombieTemplatesByAlias().keySet());
        Collections.sort(aliases);
        if (aliases.isEmpty()) {
            aliases.add("ZombieDefault");
        }
        return aliases;
    }

    // A representative HP figure for the catalogue, used only by callers that want to sanity-check a
    // generated budget against what it can actually buy.
    public static int cheapestWaveCost() {
        int cheapest = Integer.MAX_VALUE;
        for (ZombieTemplate t : ZombieRegistry.getInstance().getZombieTemplatesByAlias().values()) {
            if (t.getWavePointCost() > 0) {
                cheapest = Math.min(cheapest, t.getWavePointCost());
            }
        }
        return cheapest == Integer.MAX_VALUE ? 1 : cheapest;
    }
}
