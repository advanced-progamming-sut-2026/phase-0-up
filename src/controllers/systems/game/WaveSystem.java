package controllers.systems.game;

import factories.ZombieFactory;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.Level;
import models.game.Wave;
import models.templates.ZombieTemplate;
import utils.Constants;
import utils.Result;
import utils.registry.ZombieRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

// Drives the level's wave sequence: decides when the next wave launches, spends that wave's point
// budget on random zombies from its allowed pool, and trickles them onto random lanes at the right
// edge.
//
// The system is passive with respect to the clock -- the engine ticks it and renders whatever events
// it returns, so nothing here writes to the console or owns a loop.
//
// Timing rules:
//   * The level starts its own first wave once the opening delay has run down, counted from level
//     start. That delay is deliberately longer than any gap between waves -- it is the player's time
//     to build a defence before anything walks on.
//   * Every wave after the first needs BOTH its delay to elapse AND 75% of the previous wave's health
//     to be gone. A player who stalls does not get the next wave for free.
//
// and the final "flag" wave is twice the previous wave's difficulty, unless the level authors an
// explicit budget per wave -- an authored number always wins, which is what levels.json ships today.
public class WaveSystem {
    private final Random random;
    private final Deque<PendingSpawn> pendingSpawns = new ArrayDeque<>();
    private long lastWaveTick;
    private long lastSpawnTick;
    private Wave activeWave;

    // A zombie bought for a wave but not yet on the lawn. The wave number rides along so its arrival
    // line still names the wave that paid for it.
    private record PendingSpawn(Zombie zombie, int waveNumber) { }

    public WaveSystem() {
        this(new Random());
    }

    // Seeded variant so a wave sequence can be reproduced in a test.
    public WaveSystem(Random random) {
        this.random = random != null ? random : new Random();
    }

    public Wave getActiveWave() {
        return activeWave;
    }

    // Zombies bought for the current wave that have not walked on yet.
    public int getPendingSpawnCount() {
        return pendingSpawns.size();
    }

    // Called once per tick by the engine. Returns the events this tick produced (a wave banner, or a
    // single zombie's arrival line) for the caller to render; an empty list means nothing happened.
    public List<Result> processTick(GameSession gameSession, long currentTick) {
        List<Result> events = new ArrayList<>();
        if (gameSession == null) {
            return events;
        }
        maybeStartWave(gameSession, currentTick, events);
        releaseDueSpawns(gameSession, currentTick, events);
        return events;
    }

    // Launches the next wave if it is due. Everything that decides "is it due" lives in
    // shouldStartNextWave; this only sequences the work.
    public void maybeStartWave(GameSession gameSession, long currentTick, List<Result> events) {
        Wave next = nextWave(gameSession);
        if (next == null || !shouldStartNextWave(gameSession, next, currentTick)) {
            return;
        }
        launch(gameSession, next, currentTick, events);
    }

    // The wave the level has queued up next, or null once every wave has been launched.
    private Wave nextWave(GameSession gameSession) {
        Level level = gameSession.getLevel();
        if (level == null) {
            return null;
        }
        Wave[] waves = level.getWaves();
        if (waves == null) {
            return null;
        }
        // currentWave counts waves already started, so it doubles as the index of the next one.
        int index = gameSession.getCurrentWave();
        return index >= 0 && index < waves.length ? waves[index] : null;
    }

    // The next wave needs its delay to have elapsed AND the previous wave to be 75% destroyed. The
    // opening wave has no predecessor to grind down, so its delay alone releases it.
    private boolean shouldStartNextWave(GameSession gameSession, Wave next, long currentTick) {
        // Never overlap waves: the current one is not done arriving yet.
        if (!pendingSpawns.isEmpty()) {
            return false;
        }
        if (currentTick - lastWaveTick < delayTicksFor(gameSession, next)) {
            return false;
        }
        if (activeWave == null) {
            return true;
        }
        return activeWave.hpLostFraction() >= Constants.NEXT_WAVE_HP_THRESHOLD;
    }

    private void launch(GameSession gameSession, Wave wave, long currentTick, List<Result> events) {
        int budget = calculateBudget(gameSession, wave);
        wave.setWaveCost(budget);

        events.add(new Result(true, wave.isFinal()
                ? "The final wave has come."
                : "Wave " + wave.getNumber() + " started."));

        // Buy the whole wave up front so its total HP -- the denominator for the 75% threshold --
        // counts every zombie it will ever field, then trickle them on rather than dumping them at
        // once. A zombie still queued is alive and full HP, so it holds the threshold down until it
        // has actually walked on and been dealt with.
        for (Zombie zombie : buyZombies(gameSession, wave, budget)) {
            wave.addZombie(zombie);
            pendingSpawns.add(new PendingSpawn(zombie, wave.getNumber()));
        }

        gameSession.advanceWave();
        activeWave = wave;
        lastWaveTick = currentTick;
        // The wave's first zombie arrives with the banner rather than one interval later.
        lastSpawnTick = currentTick - spawnIntervalTicks(gameSession);
    }

    // Walks one queued zombie onto its lane per interval, so a wave arrives as a stream.
    private void releaseDueSpawns(GameSession gameSession, long currentTick, List<Result> events) {
        if (pendingSpawns.isEmpty() || currentTick - lastSpawnTick < spawnIntervalTicks(gameSession)) {
            return;
        }
        PendingSpawn pending = pendingSpawns.poll();
        Zombie zombie = pending.zombie();
        int lane = zombie.getMovement().getPositionY();

        gameSession.getMap().getRow(lane).getZombies().add(zombie);
        lastSpawnTick = currentTick;

        events.add(new Result(true, "Zombie " + zombie.getAlias() + " spawned at wave " + pending.waveNumber()
                + " in lane " + lane + " which costed " + zombie.getWavePointCost() + "."));
    }

    // An authored budget wins outright; otherwise wave 1's budget is scaled by this wave's position
    // on the difficulty curve. Either way the player's difficulty level scales the result, with the
    // default level (3) leaving authored numbers exactly as written.
    private int calculateBudget(GameSession gameSession, Wave wave) {
        int authored = wave.getWaveCost();
        double base = authored > 0
                ? authored
                : baseBudget(gameSession) * wave.difficultyFactor();
        return Math.max(0, (int) Math.round(base * difficultyScale(gameSession)));
    }

    // Wave 1's authored budget is the anchor for the curve; a level with no authored waves at all
    // falls back to a sane default rather than producing zero-cost waves that spawn nothing.
    private int baseBudget(GameSession gameSession) {
        Level level = gameSession.getLevel();
        if (level == null || level.getTemplate() == null) {
            return Constants.DEFAULT_FIRST_WAVE_BUDGET;
        }
        int first = level.getTemplate().getFirstWaveBudget();
        return first > 0 ? first : Constants.DEFAULT_FIRST_WAVE_BUDGET;
    }

    // Higher difficulty buys more zombies per wave. Mirrors SunSystem's difficulty handling so the
    // two systems scale off the same baseline.
    private double difficultyScale(GameSession gameSession) {
        return difficultyLevel(gameSession) / (double) Constants.DEFAULT_DIFFICULTY_LEVEL;
    }

    // Higher difficulty shortens the wait between waves. The opening wait is the level's authored
    // first delay or FIRST_WAVE_DELAY_SECONDS, whichever is longer, which keeps it clear of every
    // between-wave gap the levels author.
    private long delayTicksFor(GameSession gameSession, Wave wave) {
        int authored = wave.getDelay() > 0 ? wave.getDelay() : Constants.DEFAULT_WAVE_DELAY_SECONDS;
        int delaySeconds = activeWave == null
                ? Math.max(Constants.FIRST_WAVE_DELAY_SECONDS, authored)
                : authored;
        return scaledTicks(gameSession, delaySeconds);
    }

    // Higher difficulty also tightens the gap between zombies inside a wave.
    private long spawnIntervalTicks(GameSession gameSession) {
        return scaledTicks(gameSession, Constants.ZOMBIE_SPAWN_INTERVAL_SECONDS);
    }

    private long scaledTicks(GameSession gameSession, int seconds) {
        double scale = Constants.DEFAULT_DIFFICULTY_LEVEL / (double) difficultyLevel(gameSession);
        return Math.max(1L, Math.round(seconds * Constants.TICKS_PER_SECOND * scale));
    }

    private int difficultyLevel(GameSession gameSession) {
        if (gameSession.getPlayer() == null || gameSession.getPlayer().getDifficultyLevel() <= 0) {
            return Constants.DEFAULT_DIFFICULTY_LEVEL;
        }
        return gameSession.getPlayer().getDifficultyLevel();
    }

    // Spends the budget on random zombies from the wave's allowed pool: keep drawing among the types
    // that still fit in what is left until nothing does. The sum of the bought zombies' point costs
    // is therefore the wave's difficulty, which is exactly what the spec asks for. Each pick is
    // assigned a random lane here; it walks on later, when the spawn queue releases it.
    private List<Zombie> buyZombies(GameSession gameSession, Wave wave, int budget) {
        List<Zombie> bought = new ArrayList<>();
        List<ZombieTemplate> pool = resolvePool(wave.getZombieAliases());
        if (pool.isEmpty() || budget <= 0) {
            return bought;
        }

        int laneCount = gameSession.getMap().getRows().size();
        if (laneCount <= 0) {
            return bought;
        }

        int remaining = budget;
        while (true) {
            List<ZombieTemplate> affordable = affordable(pool, remaining);
            if (affordable.isEmpty()) {
                break;
            }
            ZombieTemplate pick = affordable.get(random.nextInt(affordable.size()));
            int lane = random.nextInt(laneCount);

            Zombie zombie = ZombieFactory.createZombie(pick.getAlias(), Constants.ZOMBIE_SPAWN_X, lane, gameSession);
            if (zombie == null) {
                // The registry knows this alias but the factory could not build it; drop the type so
                // the loop cannot spin on it forever.
                pool.remove(pick);
                if (pool.isEmpty()) {
                    break;
                }
                continue;
            }

            remaining -= pick.getWavePointCost();
            bought.add(zombie);
        }
        return bought;
    }

    // Resolves the wave's authored aliases against the registry. Unknown aliases and non-positive
    // costs are dropped: a free zombie would let the purchase loop run forever.
    private List<ZombieTemplate> resolvePool(List<String> allowedZombies) {
        List<ZombieTemplate> pool = new ArrayList<>();
        if (allowedZombies == null) {
            return pool;
        }
        for (String alias : allowedZombies) {
            if (alias == null) {
                continue;
            }
            ZombieTemplate template = ZombieRegistry.getInstance().getZombieTemplateByAlias(alias);
            if (template != null && template.getWavePointCost() > 0) {
                pool.add(template);
            }
        }
        return pool;
    }

    private List<ZombieTemplate> affordable(List<ZombieTemplate> pool, int remaining) {
        List<ZombieTemplate> affordable = new ArrayList<>();
        for (ZombieTemplate template : pool) {
            if (template.getWavePointCost() <= remaining) {
                affordable.add(template);
            }
        }
        return affordable;
    }
}
