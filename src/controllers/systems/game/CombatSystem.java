package controllers.systems.game;

import models.entities.collectibles.Collectibles;
import models.entities.plants.Plant;
import models.entities.projectiles.Projectile;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.greenhouse.GreenHouse;
import models.map.Cell;
import models.map.Lawnmower;
import models.map.Row;
import models.user.Profile;
import utils.Constants;
import utils.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

//this class should resolve the combat mechanics
public class CombatSystem {
    private final Random random;
    private QuestSystem questSystem;   // optional: notified of kills/losses live for quest tracking

    public CombatSystem() {
        this(new Random());
    }

    // Seeded variant so loot drops can be reproduced in a test.
    public CombatSystem(Random random) {
        this.random = random != null ? random : new Random();
    }

    // The engine wires its QuestSystem in here so combat can report kills, plant losses and mower
    // kills to the quest tally as they happen. Optional -- a standalone CombatSystem (a test) runs
    // fine without one.
    public void setQuestSystem(QuestSystem questSystem) {
        this.questSystem = questSystem;
    }

    // One frame of combat. Plants act first, then their projectiles fly, then zombies act and move,
    // then the mowers get a chance at anything that breached, and finally the casualties are cleared.
    // Returns the deaths this tick for the caller to render.
    public List<Result> processTick(GameSession session, long currentTick) {
        List<Result> events = new ArrayList<>();

        plantAttack(session, currentTick);
        resolveProjectiles(session);
        updateZombieStates(session);
        reconcileZombieLanes(session);
        checkLawnmowers(session, events);
        processDeaths(session, events);

        return events;
    }


    public void resolve(GameSession gameSession){}

    // Ticks every planted plant; its own abilities decide whether to shoot, make sun or explode.
    // Only occupied cells: Cell.getCurrentPlant() is null on an empty tile.
    private void plantAttack(GameSession gameSession, long currentTick){
        for (Row row : gameSession.getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                if (cell.hasPlant()) {
                    cell.getCurrentPlant().update(gameSession);
                }
            }
        }
    }

    // Flies every projectile and retires the ones that hit or ran out of range. Iterates a copy: a
    // hit can destroy the projectile, and an ability reacting to it can add another to the row.
    //
    // A projectile with vertical speed (the Bowling Bulb, which bounces between lanes) can end the
    // tick in a different row than the list holding it. Those are re-filed afterwards rather than
    // during the sweep: moving one mid-loop into a row not yet visited would fly it a second time in
    // the same tick.
    private void resolveProjectiles(GameSession session){
        List<Row> rows = session.getMap().getRows();
        List<LaneChange> laneChanges = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = rows.get(rowIndex);
            for (Projectile projectile : new ArrayList<>(row.getActiveProjectiles())) {
                projectile.update(session);
                if (projectile.isDestroyed()) {
                    row.removeProjectile(projectile);
                } else if (projectile.getY() != rowIndex) {
                    laneChanges.add(new LaneChange(projectile, row, projectile.getY()));
                }
            }
        }

        for (LaneChange change : laneChanges) {
            change.from().removeProjectile(change.projectile());
            if (change.toRowIndex() >= 0 && change.toRowIndex() < rows.size()) {
                rows.get(change.toRowIndex()).addProjectile(change.projectile());
            }
            // A row index off the board means the projectile left the lawn vertically; dropping it
            // from its old row (and not re-adding it) is the retirement.
        }
    }

    // A projectile that finished the tick in a different lane than the one storing it.
    private record LaneChange(Projectile projectile, Row from, int toRowIndex) { }
    // Sets off any mower whose row has been breached, then drives every running mower one step.
    //
    // The mower reports only once it has driven off the board, which is when its kill list is complete
    // -- that is the tick the summary is printed on. A mower still crossing the row has not spent
    // itself yet, so a second breach mid-run is simply mown down too; the level is only lost once the
    // mower is gone (StandardMode.checkLose).
    private void checkLawnmowers(GameSession session, List<Result> events){
        for (Row row : session.getMap().getRows()){
            Lawnmower lawnmower = row.getLawnmower();

            if(lawnmower == null || lawnmower.isUsed()){
                continue;
            }
            if(!lawnmower.isActiveNow() && hasBreached(row)){
                lawnmower.activate();
                // The header goes out on the tick the mower STARTS, not the tick it finishes.
                //
                // The sentence and its list are unchanged and still arrive in that order -- the header
                // first, then a death line for every zombie the run mows. What has moved is when the
                // death lines are raised: each now goes out on the tick the blade actually reaches its
                // zombie, instead of all of them together a second and a half later when the mower
                // leaves the board. See Lawnmower.update for why that delay was visible.
                //
                // Spacing is the spec's: "the row <r>is triggered" has no space before "is".
                events.add(new Result(true, "The lawn mower in the row " + row.getIndex()
                        + "is triggered and killed these zombies:"));
            }

            List<Zombie> killed = lawnmower.update(session);
            if (killed.isEmpty()) {
                continue;
            }

            // The mower has already pulled these zombies off the row as it passed them.
            session.recordLawnmowerKills(killed.size());   // for the Mowing Time quest
            for (Zombie zombie : killed) {
                reportZombieDeath(session, zombie, events);
            }
        }
    }
    // Has a live zombie reached the end of this row?
    //
    // Deliberately tests only isDead(), NOT Zombie.isTargetable(): that rule also excludes zombies off
    // either end of the grid, and a breach is precisely the case where one has reached or stepped past
    // x = 0. Zombie speeds almost never land exactly on the threshold, so a breaching zombie is usually
    // already at a negative x -- guarding here would stop the mower from ever firing for it, and
    // StandardMode.checkLose only ends the level once the mower is spent, so the row would stall with a
    // zombie sitting past the house forever.
    private boolean hasBreached(Row row){
        for(Zombie z : row.getZombies()){
            if (z.getHealth().isDead()){
                continue;
            }
            if(z.getMovement().getPositionX() <= Constants.LAWNMOWER_ACTIVATION_THRESHOLD){
                return true;
            }
        }
        return false;
    }
    // Clears out everything that died this tick and reports it. Runs last, so every entity has already
    // had its turn and nothing is removed out from under a system still iterating it.
    //
    // Plants are cleared before zombies within a row on purpose: a plant's death effect (Explode-o-nut
    // and friends) fires as it goes, and anything it kills in its own row is then swept up in the same
    // pass rather than lingering a tick.
    //
    // Removing a zombie from its Row does not disturb wave accounting -- a Wave holds its own list of
    // the zombies it bought, which is what the 75% next-wave threshold measures against.
    private void processDeaths(GameSession session, List<Result> events){
        for (Row row : session.getMap().getRows()) {
            for (Cell cell : row.getCells()) {
                if (cell.hasProtector() && cell.getProtector().isDead()) {
                    cell.getProtector().onDeath(session);
                    cell.removeProtector();
                }
                if (cell.hasPlant() && cell.getCurrentPlant().isDead()) {
                    Plant plant = cell.getCurrentPlant();
                    plant.onDeath(session);
                    events.add(new Result(true, "Plant " + plant.getName() + " at ("
                            + (int) cell.getX() + ", " + row.getIndex() + ") is destroyed."));
                    if (session.getMode() != null) {
                        session.getMode().onPlantDestroyed(session, plant);
                    }
                    cell.removePlant();
                    session.recordPlantLost();
                }
            }

            for (Zombie zombie : new ArrayList<>(row.getZombies())) {
                // Anything that walks off the FAR edge is gone, whatever turned it round.
                //
                // This used to require isHypnotized(), which left one zombie able to leave the board and
                // never leave the game: a Prospector blows itself back to column 0 and walks right for
                // the rest of the level, and past the edge it simply kept going. It was still in the
                // row, still alive, so checkWin's living-zombie count never reached zero and the level
                // could not be finished -- and the wave gate, which waits on 75% of a wave's HP being
                // gone, was holding a full-health zombie somewhere off in the desert.
                //
                // Past ZOMBIE_SPAWN_X rather than past BOARD_COLS, because zombies SPAWN at 9.5: a
                // threshold at 9 would delete every zombie on the tick it walked on.
                if (zombie.getMovement().getPositionX() > Constants.ZOMBIE_SPAWN_X) {
                    // Zeroed, but NOT through reportZombieDeath. The player did not kill this one, so it
                    // earns no kill tally, no loot, no quest credit and no Meow Points -- while the wave
                    // accounting, which sums the HP of everything it spawned, still resolves.
                    zombie.getHealth().applyDamage(zombie.getHealth().getTotalHP(),
                            models.entities.projectiles.Element.NEUTRAL, null);
                    row.getZombies().remove(zombie);
                    events.add(new Result(true, "The " + zombie.getAlias()
                            + " wanders off the far end of the lawn and is gone."));
                    continue;
                }
                if (!zombie.getHealth().isDead()) {
                    continue;
                }
                unlink(session, row, zombie);
                reportZombieDeath(session, zombie, events);
            }
        }
    }

    // Takes a dead zombie off the board -- out of EVERY row holding it, not just the one being swept.
    //
    // For the ordinary zombie those are the same thing and this is one list removal. For a Zomboss they
    // are not: it is a member of both of its rows (see Zombie.rowSpan), so the sweep would reach it a
    // second time when it got to the lower row and report the same death twice -- two death lines, two
    // kill tallies, two lots of loot. Clearing both here means the second row's turn comes round to
    // find it already gone, because processDeaths copies each row's list at that row's own turn.
    private void unlink(GameSession session, Row row, Zombie zombie) {
        if (zombie.rowSpan() <= 1) {
            row.getZombies().remove(zombie);
            return;
        }
        for (Row other : session.getMap().getRows()) {
            other.getZombies().remove(zombie);
        }
    }

    // One place every zombie death passes through, whichever system did the killing: prints the death
    // line (spec has no trailing period -- "Zombie of type <type> is dead at (<x>, y>)"), tallies the
    // kill, and rolls the glowing-zombie plant food and the 10% loot drop. Removal from the row is the
    // caller's job, since the mower and processDeaths remove at different points in their flow.
    private void reportZombieDeath(GameSession session, Zombie zombie, List<Result> events) {
        events.add(new Result(true, "Zombie of type " + zombie.getAlias() + " is dead at ("
                + (int) zombie.getMovement().getPositionX() + ", "
                + zombie.getMovement().getPositionY() + ")"));
        session.recordZombieKilled();
        Plant killer = zombie.getHealth().getLastAttacker();
        if (questSystem != null) {
            questSystem.recordZombieKilled(zombie, killer);
            recordMowerlessFirstColumnKill(session, zombie, killer);
        }
        // A mode that scores kills (the scoring game) hears about them here rather than polling the
        // board each frame. Every death route -- shots, explosions, mowers, cheats -- funnels through
        // this method, so one notification covers them all and the tick loop stays untouched.
        if (session.getMode() instanceof models.game.scoring.ZombieDeathListener listener) {
            listener.onZombieKilled(session, zombie, killer, session.getTimeTicks());
        }
        dropPlantFood(session, zombie, events);
        dropStolenSun(session, zombie, events);
        awardCarriedDrop(session, zombie, events);
    }

    // A sun-stealing zombie (Ra and friends) spills part of its haul back onto the lawn when it dies --
    // StealSunAbility decides how much (half of what it took). Without this the ability banked the sun
    // and the player could never win it back, which is not what the drop-on-death rule says.
    private void dropStolenSun(GameSession session, Zombie zombie, List<Result> events) {
        for (models.entities.zombies.Abilities.ZombieAbility ability : zombie.getAbilities()) {
            // Two different thefts pay out here: the Turquoise drained the player's bank and gives back
            // half, while Ra pocketed sun off the lawn and gives back all of it.
            int refund;
            if (ability instanceof models.entities.zombies.Abilities.StealSunAbility turquoise) {
                refund = turquoise.getSunDropAmountOnDeath();
            } else if (ability instanceof models.entities.zombies.Abilities.StealGroundSunAbility ra) {
                refund = ra.getSunDropAmountOnDeath();
            } else {
                continue;
            }
            if (refund <= 0) {
                continue;
            }
            spillSun(session, zombie, refund);
            events.add(new Result(true, "The " + zombie.getAlias() + " drops " + spilledTotal(refund)
                    + " of the sun it stole onto the lawn -- go and get it!"));
        }
    }

    // How much sun each dropped token is worth. The standard sky sun, so a haul comes back as a handful
    // of ordinary suns rather than one enormous one.
    private static final int SPILLED_SUN_PER_TOKEN = 25;
    // A cap on the tokens one corpse can produce, so a Ra that has been hoarding for a minute does not
    // bury its own tile under fifty overlapping sprites.
    private static final int MAX_SPILLED_TOKENS = 8;

    // Puts the stolen sun back ON THE GROUND rather than into the bank.
    //
    // It used to be credited outright, which is the one thing a "drops" rule cannot mean: the counter
    // ticked up and there was nothing to see, nothing to collect and no reason to have killed the thief
    // where you did. Sun the player has to walk over and pick up is the whole point of taking it back.
    //
    // Spread across the tiles behind the corpse -- toward the house, which is where a zombie's momentum
    // would carry a spill -- so a big haul is several suns to sweep up rather than a stack on one tile.
    // Sun clamps its own position to the board, so a thief that dies at the very edge still drops
    // everything somewhere collectable.
    // Every dropped sun is worth exactly 25, and the haul is rounded to the nearest multiple of it.
    //
    // Not divided into equal shares, which is what this did first: a 37-sun haul came out as two suns
    // worth 19 and 18, and a sun worth 18 is a thing that exists nowhere else in the game. A sun the
    // player picks up is worth 25, always -- so the count is what varies, and a remainder under half a
    // sun is simply rounded away rather than turned into a fraction of one.
    private void spillSun(GameSession session, Zombie zombie, int refund) {
        int tokens = Math.min(MAX_SPILLED_TOKENS,
                Math.max(1, Math.round(refund / (float) SPILLED_SUN_PER_TOKEN)));
        int row = zombie.getMovement().getPositionY();
        double x = zombie.getMovement().getPositionX();
        for (int i = 0; i < tokens; i++) {
            double dropX = x - (i * 0.5);
            session.addSun(new models.entities.collectibles.Sun(dropX, row, row,
                    models.entities.collectibles.SunType.NORMAL, SPILLED_SUN_PER_TOKEN, false,
                    SPILLED_SUN_EXPIRE_TICKS));
        }
    }

    // What a spill is actually worth once rounded, for the sentence that announces it -- so the number
    // the player reads is the number they can pick up.
    private static int spilledTotal(int refund) {
        return Math.min(MAX_SPILLED_TOKENS,
                Math.max(1, Math.round(refund / (float) SPILLED_SUN_PER_TOKEN))) * SPILLED_SUN_PER_TOKEN;
    }

    // Long enough to be worth crossing the lawn for, short enough that a corpse's spill is not still
    // sitting there at the end of the level. Matches the sky sun's ground life.
    private static final int SPILLED_SUN_EXPIRE_TICKS = 240;

    // Credits the Almost Victorious quest when a plant fells a zombie standing in column 0 of a row
    // whose lawn mower is already spent -- a last-ditch kill with no mower left as a safety net. The
    // mower's own kills are excluded: they carry no killer plant (killer == null), so requiring a
    // killer both skips them and matches the quest's intent ("kill" a zombie there with a plant).
    private void recordMowerlessFirstColumnKill(GameSession session, Zombie zombie, Plant killer) {
        if (killer == null) {
            return;
        }
        int rowIndex = zombie.getMovement().getPositionY();
        if (rowIndex < 0 || rowIndex >= session.getMap().getRows().size()) {
            return;
        }
        Lawnmower mower = session.getMap().getRow(rowIndex).getLawnmower();
        if (mower != null && mower.isUsed()
                && zombie.getMovement().getPositionX() < Constants.FIRST_COLUMN_MAX_X) {
            questSystem.recordMowerlessFirstColumnKill();
        }
    }

    // A glowing zombie LEAVES a plant food on the lawn as it dies. Whether it glows was settled at
    // spawn (ZombieFactory, 5%).
    //
    // It used to be paid straight into the counter here. Now it is dropped where the zombie fell and
    // has to be picked up -- clicked in the GUI, `collect plant-food -l (x, y)` in the terminal -- and
    // it goes stale after PlantFood.EXPIRE_TICKS if nobody does. The credit therefore happens in
    // PlantFood.applyEffect, not here.
    //
    // The sentence below is UNCHANGED and still fires on the death, not on the pickup: it is quoted
    // verbatim from the project document (the misspelling "dropeed" is the document's), and the
    // document asks for it whenever a glowing zombie dies. It reports the count as it stands at that
    // moment, which is now the count BEFORE the pickup -- the second line is what tells the player
    // there is something to go and get.
    private void dropPlantFood(GameSession session, Zombie zombie, List<Result> events) {
        if (!zombie.isGlowing()) {
            return;
        }
        models.entities.collectibles.PlantFood dropped = new models.entities.collectibles.PlantFood(
                zombie.getMovement().getPositionX(), zombie.getMovement().getPositionY());
        session.addPlantFood(dropped);

        events.add(new Result(true, "The glowing zombie dropeed a plant food; you have "
                + session.getPlantFoodCount() + " plant foods now."));
        events.add(new Result(true, "Plant food is glowing on the lawn at ("
                + dropped.tileColumn() + ", " + dropped.tileRow() + ") -- grab it before it fades!"));
    }

    // Hands over whatever this zombie walked on carrying. The 10% roll itself lives in ZombieFactory
    // now, beside the glow roll, because the board marks a carrier while it is still alive and a die
    // rolled here could not be read by anything until the zombie was already gone. Odds and draw are
    // unchanged; only the moment moved.
    //
    // The one thing that can go stale between the two moments is the greenhouse filling up. A pot that
    // no longer fits pays out as a coin rather than as nothing: the player was promised a drop, the
    // zombie was visibly carrying one, and silently swallowing it is the exact decay the pool check
    // exists to avoid.
    private void awardCarriedDrop(GameSession session, Zombie zombie, List<Result> events) {
        Collectibles carried = zombie.getCarriedDrop();
        if (carried == null) {
            return;
        }
        Profile profile = session.getPlayer();
        if (profile == null) {
            return;
        }
        GreenHouse greenHouse = profile.getMyGreenHouse();
        if (carried == Collectibles.POT && (greenHouse == null || greenHouse.isFull())) {
            carried = Collectibles.COIN;
        }

        switch (carried) {
            case COIN:
                profile.addCoins(Constants.DROP_COIN_AMOUNT);
                events.add(dropped("coin", profile.getCoins(), "coins"));
                break;
            case GEM:
                profile.addGems(Constants.DROP_GEM_AMOUNT);
                events.add(dropped("gem", profile.getGems(), "gems"));
                break;
            case POT:
                greenHouse.unlockNextPot();
                events.add(dropped("pot", greenHouse.getUnlockedPots().size(), "pots"));
                break;
            default:
                break;
        }
    }

    // Shape and the spec's "dropeed" typo are verbatim (project.md line 984). ONE deliberate
    // deviation: "diamond" is rendered "gem" -- a product decision; do not "restore" it.
    private Result dropped(String item, int total, String plural) {
        return new Result(true, "A zombie dropeed a " + item + "; you have " + total + " " + plural + " now.");
    }
    // Ticks every zombie: status timers decay, its abilities run (eating included -- that is why there
    // is no separate zombieAttack pass), then it moves. Iterates a copy because an ability can add or
    // remove zombies from the row mid-loop (a Barrel roller spawning imps on death, for one).
    private void updateZombieStates(GameSession session){
        for (Row row : session.getMap().getRows()) {
            for (Zombie zombie : new ArrayList<>(row.getZombies())) {
                zombie.update(session);
            }
        }
    }
    // Re-files a lane-changed zombie: movement moves y, but lane queries read Row membership.
    private void reconcileZombieLanes(GameSession session){
        List<Row> rows = session.getMap().getRows();
        List<ZombieLaneChange> laneChanges = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = rows.get(rowIndex);
            for (Zombie zombie : row.getZombies()) {
                // A zombie that stands in more than one row files ITSELF, and disagreeing with the row
                // holding it is its normal state rather than a lane change: a Zomboss straddling rows
                // 2 and 3 has movement.y == 2 while row 3 also holds it. Re-filing that on sight would
                // pull the boss out of its lower row on the very first tick and leave the plants there
                // shooting at nothing. See Zombie.rowSpan.
                if (zombie.rowSpan() > 1) {
                    continue;
                }
                int lane = zombie.getMovement().getPositionY();
                if (lane != rowIndex && lane >= 0 && lane < rows.size()) {
                    laneChanges.add(new ZombieLaneChange(zombie, row, lane));
                }
            }
        }
        for (ZombieLaneChange change : laneChanges) {
            change.from().getZombies().remove(change.zombie());
            rows.get(change.toRowIndex()).getZombies().add(change.zombie());
        }
    }
    private record ZombieLaneChange(Zombie zombie, Row from, int toRowIndex) { }
}
