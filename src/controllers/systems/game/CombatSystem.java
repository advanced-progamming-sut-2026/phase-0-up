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

    public CombatSystem() {
        this(new Random());
    }

    // Seeded variant so loot drops can be reproduced in a test.
    public CombatSystem(Random random) {
        this.random = random != null ? random : new Random();
    }

    // One frame of combat. Plants act first, then their projectiles fly, then zombies act and move,
    // then the mowers get a chance at anything that breached, and finally the casualties are cleared.
    // Returns the deaths this tick for the caller to render.
    public List<Result> processTick(GameSession session, long currentTick) {
        List<Result> events = new ArrayList<>();

        plantAttack(session, currentTick);
        resolveProjectiles(session);
        updateZombieStates(session);
        checkLawnmowers(session);
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
    private void checkLawnmowers(GameSession session){
        for (Row row : session.getMap().getRows()){
            Lawnmower lawnmower = row.getLawnmower();

            if(lawnmower == null || lawnmower.isUsed()){
                continue;
            }
            if(!lawnmower.isActiveNow()){
                tryActivateLawnmower(lawnmower, row.getZombies());
            }
            // Drive a running mower -- including one that triggered on this very tick, so the zombie
            // that set it off is mown on the same tick rather than getting a free step first.
            lawnmower.update(session);
        }
    }
    // Deliberately tests only isDead(), NOT Zombie.isTargetable(): that rule also excludes zombies
    // off either end of the grid, and a breach is precisely the case where one has reached or stepped
    // past x = 0. Zombie speeds almost never land exactly on the threshold, so a breaching zombie is
    // usually already at a negative x -- guarding here would stop the mower from ever firing for it,
    // and StandardMode.checkLose only ends the level once the mower is spent, so the row would stall
    // with a zombie sitting past the house forever.
    private void tryActivateLawnmower(Lawnmower lawnmower , List<Zombie> zombies){
        for(Zombie z : zombies){
            if (z.getHealth().isDead()){
                continue;
            }
            if(z.getMovement().getPositionX() <= Constants.LAWNMOWER_ACTIVATION_THRESHOLD){
                lawnmower.activate();
                return;
            }
        }
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
                }
            }

            for (Zombie zombie : new ArrayList<>(row.getZombies())) {
                if (!zombie.getHealth().isDead()) {
                    continue;
                }
                events.add(new Result(true, "Zombie of type " + zombie.getAlias() + " is dead at ("
                        + (int) zombie.getMovement().getPositionX() + ", "
                        + zombie.getMovement().getPositionY() + ")."));
                row.getZombies().remove(zombie);
                session.recordZombieKilled();
                dropPlantFood(session, zombie, events);
                rollLootDrop(session, events);
            }
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
