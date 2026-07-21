package models.game.scoring;

import models.entities.plants.Plant;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Lawnmower;
import models.map.Row;
import utils.Constants;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// The Meow Point engine: the scoring game's whole rulebook, kept off the tick loop.
//
// Three of the five rules are reactions to a kill and are driven purely by ZombieDeathListener
// callbacks, so nothing here polls and nothing is checked every frame. The other two are settlement
// rules -- they read the final board once, when the level is over.
//
// The engine keeps a per-rule tally as well as a running total, because the end-of-level card shows the
// player WHY they scored what they did; a bare number teaches them nothing about which strategy paid.
public class MeowPointManager {

    // Two kills land "simultaneously" if they happen within this many ticks of each other.
    private static final int SIMULTANEOUS_WINDOW_TICKS = Constants.TICKS_PER_SECOND;
    // A kill counts as a speed kill if the zombie dies within this long of spawning.
    private static final int SPEED_KILL_TICKS = 5 * Constants.TICKS_PER_SECOND;
    // Sun left over is converted at this rate: every SUN_PER_POINT unused sun is worth one Meow Point.
    private static final int SUN_PER_POINT = 10;
    // The plant category that counts as "explosive" for the one-shot rule.
    private static final String EXPLOSIVE_CATEGORY = "EXPLOSIVE";

    private final Map<MeowPointRule, Integer> hits = new EnumMap<>(MeowPointRule.class);
    private final Map<MeowPointRule, Integer> points = new EnumMap<>(MeowPointRule.class);
    // Ticks of the kills still inside the simultaneous-kill window, oldest first.
    private final List<Long> recentKillTicks = new ArrayList<>();
    private int total;
    private int kills;
    private boolean settled;

    // --- Live rules: driven by kills ------------------------------------------------------------

    // Scores one confirmed kill against rules 1-3. Called from the combat loop's single death choke
    // point, so every kill route (shots, explosions, mowers, cheats) reaches it exactly once.
    public void recordKill(Zombie zombie, Plant killer, long tick) {
        if (zombie == null || settled) {
            return;
        }
        kills++;
        scoreSimultaneous(tick);
        scoreSpeedKill(zombie, tick);
        scoreOneShot(zombie, killer);
    }

    // Rule 1 -- two or more kills inside one second. The award fires once per burst, on the kill that
    // makes it a double; a third and fourth kill in the same window do not re-award, otherwise a single
    // Cherry Bomb would pay out several times for what the player experienced as one play.
    private void scoreSimultaneous(long tick) {
        recentKillTicks.removeIf(t -> tick - t >= SIMULTANEOUS_WINDOW_TICKS);
        recentKillTicks.add(tick);
        if (recentKillTicks.size() == 2) {
            award(MeowPointRule.SIMULTANEOUS_KILL, 1);
        }
    }

    // Rule 2 -- felled within five seconds of walking on. Zombies built outside a session carry a
    // spawn tick of -1 and are skipped rather than scoring an accidental bonus.
    private void scoreSpeedKill(Zombie zombie, long tick) {
        long spawn = zombie.getSpawnTick();
        if (spawn >= 0 && tick - spawn <= SPEED_KILL_TICKS) {
            award(MeowPointRule.SPEED_KILL, 1);
        }
    }

    // Rule 3 -- an explosive plant killing a zombie outright from full health. Both halves matter: a
    // Cherry Bomb finishing off a nearly-dead zombie is not the play being rewarded.
    private void scoreOneShot(Zombie zombie, Plant killer) {
        if (killer == null || !isExplosive(killer)) {
            return;
        }
        if (zombie.getHealth() != null && zombie.getHealth().wasKilledFromFullHealth()) {
            award(MeowPointRule.ONE_SHOT, 1);
        }
    }

    private boolean isExplosive(Plant plant) {
        String category = plant.getCategory();
        return category != null && category.trim().equalsIgnoreCase(EXPLOSIVE_CATEGORY);
    }

    // --- Settlement rules: read the final board once ---------------------------------------------

    // Closes the scorecard: applies the two end-of-level rules and freezes the total. Safe to call
    // more than once -- a level that ends on the same tick it is abandoned must not score twice.
    public void settle(GameSession session) {
        if (settled || session == null) {
            return;
        }
        settled = true;

        // Rule 4 -- unused sun, one point per ten. Integer division is the rule, not a rounding
        // accident: 99 leftover sun is worth 9, the tenth point needs the hundredth sun.
        int sunPoints = Math.max(0, session.getSunAmount()) / SUN_PER_POINT;
        if (sunPoints > 0) {
            award(MeowPointRule.SUN_HOARDER, sunPoints);
        }

        // Rule 5 -- every lawn mower never spent. A mode with no mowers at all simply scores nothing
        // here rather than counting nulls as intact.
        int intact = 0;
        for (Row row : session.getMap().getRows()) {
            Lawnmower mower = row.getLawnmower();
            if (mower != null && !mower.isUsed()) {
                intact++;
            }
        }
        if (intact > 0) {
            award(MeowPointRule.FLAWLESS_DEFENSE, intact);
        }
    }

    private void award(MeowPointRule rule, int times) {
        if (times <= 0) {
            return;
        }
        hits.merge(rule, times, Integer::sum);
        int gained = rule.getAward() * times;
        points.merge(rule, gained, Integer::sum);
        total += gained;
    }

    // --- Reporting --------------------------------------------------------------------------------

    public int getTotal() {
        return total;
    }

    public int getKills() {
        return kills;
    }

    public boolean isSettled() {
        return settled;
    }

    public int getHits(MeowPointRule rule) {
        return hits.getOrDefault(rule, 0);
    }

    public int getPoints(MeowPointRule rule) {
        return points.getOrDefault(rule, 0);
    }

    // The end-of-level card: every rule that actually paid, what triggered it, and the final tally.
    // Rules that never fired are left out so the card shows what the player DID, not what they missed.
    public String buildScorecard() {
        StringBuilder sb = new StringBuilder();
        sb.append("===== MEOW POINTS =====\n");
        boolean any = false;
        for (MeowPointRule rule : MeowPointRule.values()) {
            int n = getHits(rule);
            if (n == 0) {
                continue;
            }
            any = true;
            sb.append(String.format("  %-20s x%-3d %+5d%n", rule.getLabel(), n, getPoints(rule)));
        }
        if (!any) {
            sb.append("  No bonuses earned this run.\n");
        }
        sb.append(String.format("  %-20s     %5d%n", "TOTAL", total));
        sb.append("Zombies destroyed: ").append(kills);
        return sb.toString();
    }
}
