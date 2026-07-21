package models.game.gamemodes;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.scoring.MeowPointManager;
import models.game.scoring.ZombieDeathListener;

import java.time.LocalDate;

// The scoring game (بازی امتیازی). It plays exactly like an adventure level -- same board, same waves,
// same win/lose rules, which is why it extends StandardMode rather than reinventing them -- but what
// the run is judged on is the Meow Point total at the end, not merely surviving.
//
// Two things make it its own mode:
//
//   1. The lawn is the SAME for everyone on a given day. Every random choice that shapes the run --
//      which zombies a wave buys, which lanes they walk down -- is driven off a seed derived from the
//      calendar date, so two players opening the game on the same day face an identical assault and
//      their scores are actually comparable. See dailySeed().
//
//   2. It scores itself. The mode is the ZombieDeathListener for its own session, so the combat loop
//      hands it every kill and the Meow Point rules run off those events instead of being polled each
//      frame.
public class ScoringMode extends StandardMode implements ZombieDeathListener {

    // Mixed into the date so this mode's seed does not collide with any other daily-seeded feature
    // that might later derive its own stream from the same epoch day.
    private static final long SEED_SALT = 0x5C0_5EEDL;

    private final MeowPointManager meowPoints = new MeowPointManager();
    private final long seed;
    private final LocalDate day;

    public ScoringMode() {
        this(LocalDate.now());
    }

    // Explicit-date variant: lets a test pin the day and assert that the same date always produces the
    // same lawn, without waiting for the calendar to roll over.
    public ScoringMode(LocalDate day) {
        this.day = day != null ? day : LocalDate.now();
        this.seed = dailySeed(this.day);
    }

    // The day's seed. Derived from the epoch day so it changes exactly at midnight and is identical
    // for every player in the world on that date.
    public static long dailySeed(LocalDate day) {
        return (day.toEpochDay() * 0x9E3779B97F4A7C15L) ^ SEED_SALT;
    }

    public long getSeed() {
        return seed;
    }

    public LocalDate getDay() {
        return day;
    }

    public MeowPointManager getMeowPoints() {
        return meowPoints;
    }

    @Override
    public void onStart(GameSession session) {
        super.onStart(session);
        session.reportEvent("Scoring Game for " + day + " -- every player faces this same lawn today. "
                + "Play for Meow Points!");
    }

    // Rules 1-3 are scored here, off the combat loop's kill notification.
    @Override
    public void onZombieKilled(GameSession session, Zombie zombie, Plant killer, long tick) {
        meowPoints.recordKill(zombie, killer, tick);
    }

    // Closes the scorecard the moment the level resolves, so the leftover sun and surviving mowers are
    // read from the board as it actually finished. Winning or losing both settle -- a lost run still
    // earned whatever it earned.
    public int settleAndScore(GameSession session) {
        meowPoints.settle(session);
        return meowPoints.getTotal();
    }
}
