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
    private void resolveProjectiles(GameSession session){
        for (Row row : session.getMap().getRows()) {
            for (Projectile projectile : new ArrayList<>(row.getActiveProjectiles())) {
                projectile.update(session);
                if (projectile.isDestroyed()) {
                    row.removeProjectile(projectile);
                }
            }
        }
    }
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
            }

            List<Zombie> killed = lawnmower.update(session);
            if (killed.isEmpty()) {
                continue;
            }

            // The mower has already pulled these zombies off the row as it passed them; it reports the
            // whole run here, in one go, the tick it drives off the board. Spacing is the spec's: "the
            // row <r>is triggered" has no space before "is".
            events.add(new Result(true, "The lawn mower in the row " + row.getIndex()
                    + "is triggered and killed these zombies:"));
            session.recordLawnmowerKills(killed.size());   // for the Mowing Time quest
            if (questSystem != null) {
                questSystem.recordLawnmowerKills(killed.size());
            }
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
                    cell.removePlant();
                    session.recordPlantLost();
                    if (questSystem != null) {
                        questSystem.recordPlantLost();
                    }
                }
            }

            for (Zombie zombie : new ArrayList<>(row.getZombies())) {
                if (!zombie.getHealth().isDead()) {
                    continue;
                }
                row.getZombies().remove(zombie);
                reportZombieDeath(session, zombie, events);
            }
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
        if (questSystem != null) {
            Plant killer = zombie.getHealth().getLastAttacker();
            questSystem.recordZombieKilled(zombie, killer);
            recordMowerlessFirstColumnKill(session, zombie, killer);
        }
        dropPlantFood(session, zombie, events);
        rollLootDrop(session, events);
    }

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

    // A glowing zombie hands the player a plant food as it dies. Whether it glows was settled at spawn
    // (ZombieFactory, 5%). The player can hold three at most, so the extra is lost on a full stock --
    // the message still prints, as the spec asks for it whenever a glowing zombie dies, and simply
    // reports the unchanged total.
    private void dropPlantFood(GameSession session, Zombie zombie, List<Result> events) {
        if (!zombie.isGlowing()) {
            return;
        }
        session.increasePlantFoodCount(1);
        events.add(new Result(true, "The glowing zombie dropeed a plant food; you have "
                + session.getPlantFoodCount() + " plant foods now."));
    }

    // A dying zombie has a 10% chance to leave something behind: 50 coins, 1 diamond, or a greenhouse
    // pot, drawn evenly. Coins and diamonds land straight in the player's profile -- there is nothing
    // to walk over and pick up. The spec fixes the 10% but not the split between the three.
    //
    // A pot only joins the draw while the greenhouse has room for one. The greenhouse holds 20 and
    // starts with 5, so it fills for good after 15 pots -- keeping POT in the draw after that would
    // quietly swallow a third of every drop and decay the player's promised 10% down to ~6.7%.
    private void rollLootDrop(GameSession session, List<Result> events) {
        if (random.nextDouble() >= Constants.ZOMBIE_DROP_PROBABILITY) {
            return;
        }
        Profile profile = session.getPlayer();
        if (profile == null) {
            return;
        }

        GreenHouse greenHouse = profile.getMyGreenHouse();

        List<Collectibles> pool = new ArrayList<>();
        pool.add(Collectibles.COIN);
        pool.add(Collectibles.GEM);
        if (greenHouse != null && !greenHouse.isFull()) {
            pool.add(Collectibles.POT);
        }

        switch (pool.get(random.nextInt(pool.size()))) {
            case COIN:
                profile.addCoins(Constants.DROP_COIN_AMOUNT);
                events.add(dropped("coin", profile.getCoins(), "coins"));
                break;
            case GEM:
                profile.addGems(Constants.DROP_DIAMOND_AMOUNT);
                events.add(dropped("diamond", profile.getGems(), "diamonds"));
                break;
            case POT:
                greenHouse.unlockNextPot();
                events.add(dropped("pot", greenHouse.getUnlockedPots().size(), "pots"));
                break;
        }
    }

    // Spelling is the spec's, verbatim (documents/project.md: "A zombie dropeed a <coin/diamond/pot>;
    // you have <n> <coins/diamonds/pots> now."), so the output matches what is graded.
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
}
