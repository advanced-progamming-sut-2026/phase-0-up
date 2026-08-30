package controllers.systems.game;

import models.entities.collectibles.PlantFood;
import models.game.GameSession;
import utils.Constants;
import utils.Result;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// Runs the plant food lying on the lawn: ages it, sweeps what has gone stale, and hands one over when
// the player names its tile.
//
// Small on purpose, and separate from SunSystem on purpose. SunSystem is not "the collectibles
// system" -- it is the sun ECONOMY: a spawn cadence tuned to the level and difficulty, a Dark Ages
// exception, a radioactive variant that explodes if caught in the air, and a theft path for the Ra
// Zombie. None of that applies to a drop that already exists, is always worth one, and only has to
// last a few seconds. Folding this in would mean every one of those rules starting by asking what
// kind of collectible it was holding.
public class PlantFoodSystem {

    // How long before a stale drop is swept, in ticks. The pickup itself carries the number, since the
    // view reads it too when it decides to start flashing.
    public static final int EXPIRE_TICKS = PlantFood.EXPIRE_TICKS;

    // Ages every drop on the lawn and clears the ones nobody reached.
    //
    // Returns the events for the caller to render, exactly as CombatSystem.processTick does -- a
    // fading pickup is worth a line, because a plant food that simply disappears reads as a bug.
    public List<Result> onTick(GameSession gameSession) {
        List<Result> results = new ArrayList<>();
        Iterator<PlantFood> pickups = gameSession.getActivePlantFoods().iterator();
        while (pickups.hasNext()) {
            PlantFood pickup = pickups.next();
            boolean wasLive = !pickup.isRemovable();
            pickup.update(gameSession);
            if (!pickup.isRemovable()) {
                continue;
            }
            // Only an expiry is announced. A collected one is already reported by the command that
            // collected it, and saying so twice would read as two plant foods.
            if (wasLive && !pickup.isCollected()) {
                results.add(new Result(true, "The plant food at (" + pickup.tileColumn() + ", "
                        + pickup.tileRow() + ") fizzles out. Too slow!"));
            }
            pickups.remove();
        }
        return results;
    }

    // Picks up whatever is on the named tile. False when there is nothing there, which is what makes
    // a stray click harmless.
    public boolean collectPlantFood(GameSession gameSession, int x, int y) {
        PlantFood pickup = findPlantFoodAt(gameSession, x, y);
        if (pickup == null) {
            return false;
        }
        pickup.collect(gameSession);
        gameSession.getActivePlantFoods().remove(pickup);
        return true;
    }

    // The tile test, asked of the pickup rather than worked out here. See PlantFood.tileColumn for why
    // that rule lives in exactly one place.
    private PlantFood findPlantFoodAt(GameSession gameSession, int x, int y) {
        if (x < 0 || x >= Constants.BOARD_COLS || y < 0 || y >= Constants.BOARD_ROWS) {
            return null;
        }
        for (PlantFood pickup : gameSession.getActivePlantFoods()) {
            if (pickup.isRemovable()) {
                continue;
            }
            if (pickup.tileColumn() == x && pickup.tileRow() == y) {
                return pickup;
            }
        }
        return null;
    }
}
