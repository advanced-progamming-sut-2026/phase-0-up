package models.entities.collectibles;

import controllers.systems.game.CombatSystem;
import controllers.systems.game.PlantFoodSystem;
import factories.LevelFactory;
import factories.ZombieFactory;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.Level;
import models.user.Profile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.Constants;
import utils.Result;
import utils.gameinitializers.GameInitializer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Plant food as a thing on the lawn rather than a number that goes up.
//
// A glowing zombie used to pay its plant food straight into the counter as it died -- correct
// arithmetic and nothing to see or do. It is now DROPPED where the zombie fell, has to be picked up,
// and goes stale if nobody reaches it. That turns one line of bookkeeping into four rules that can
// each fail on their own, so each is pinned here.
//
// The one that matters most is the last: a drop that lands on a tile the collect command refuses is
// visible, clickable and unobtainable. Sun has had that exact bug twice (see Sun.onBoardX), which is
// why the clamp is copied and why it is tested from the zombie positions that actually occur rather
// than from tidy ones.
class PlantFoodPickupTest {

    private static final String LEVEL_ID = "s1l1";
    private static final int ROW = 2;

    @BeforeAll
    static void loadGameData() {
        new GameInitializer().loadAllData();
    }

    private static GameSession session() {
        Level level = LevelFactory.createLevel(LEVEL_ID);
        GameSession gameSession = new GameSession(new Profile(), level);
        // Start from empty, so "the count went up by one" is a real assertion rather than a comparison
        // against whatever the profile happened to be carrying.
        gameSession.decreasePlantFoodCount(gameSession.getPlantFoodCount());
        return gameSession;
    }

    // A zombie that is about to die, glowing or not.
    private static Zombie doomed(GameSession gameSession, double x, boolean glowing) {
        Zombie zombie = ZombieFactory.createZombie("ZombieDefault", x, ROW, gameSession);
        assertNotNull(zombie);
        zombie.setGlowing(glowing);
        gameSession.getMap().getRow(ROW).getZombies().add(zombie);
        zombie.getHealth().applyDamage(zombie.getHealth().getTotalHP(),
                models.entities.projectiles.Element.NEUTRAL, null);
        return zombie;
    }

    // Runs the tick that sweeps the dead, which is what drops the pickup.
    private static List<Result> killTick(GameSession gameSession) {
        return new CombatSystem().processTick(gameSession, 1);
    }

    // ---- the drop --------------------------------------------------------------------------------

    @Test
    void aGlowingZombieLeavesAPickupWhereItFell() {
        GameSession gameSession = session();
        doomed(gameSession, 4.4, true);

        killTick(gameSession);

        assertEquals(1, gameSession.getActivePlantFoods().size());
        PlantFood dropped = gameSession.getActivePlantFoods().get(0);
        assertEquals(4, dropped.tileColumn(), "it lands on the tile the zombie died on");
        assertEquals(ROW, dropped.tileRow());
    }

    // The whole point of the change: the counter does NOT move until somebody picks it up.
    @Test
    void theCounterDoesNotMoveUntilItIsCollected() {
        GameSession gameSession = session();
        doomed(gameSession, 4.4, true);

        killTick(gameSession);

        assertEquals(0, gameSession.getPlantFoodCount(),
                "the drop is on the lawn, not in the pouch");
    }

    @Test
    void anOrdinaryZombieLeavesNothing() {
        GameSession gameSession = session();
        doomed(gameSession, 4.4, false);

        killTick(gameSession);

        assertTrue(gameSession.getActivePlantFoods().isEmpty());
    }

    // ---- collecting ------------------------------------------------------------------------------

    @Test
    void collectingItCreditsThePouchAndClearsTheLawn() {
        GameSession gameSession = session();
        doomed(gameSession, 4.4, true);
        killTick(gameSession);

        boolean collected = new PlantFoodSystem().collectPlantFood(gameSession, 4, ROW);

        assertTrue(collected);
        assertEquals(1, gameSession.getPlantFoodCount());
        assertTrue(gameSession.getActivePlantFoods().isEmpty());
    }

    // A miss has to be harmless and has to say so, because the GUI fires this on any bare click.
    @Test
    void collectingAnEmptyTileDoesNothing() {
        GameSession gameSession = session();
        doomed(gameSession, 4.4, true);
        killTick(gameSession);

        assertFalse(new PlantFoodSystem().collectPlantFood(gameSession, 7, ROW));
        assertEquals(0, gameSession.getPlantFoodCount());
        assertEquals(1, gameSession.getActivePlantFoods().size(), "the real one is still there");
    }

    @Test
    void thereIsNothingToCollectTwice() {
        GameSession gameSession = session();
        doomed(gameSession, 4.4, true);
        killTick(gameSession);

        PlantFoodSystem plantFood = new PlantFoodSystem();
        assertTrue(plantFood.collectPlantFood(gameSession, 4, ROW));
        assertFalse(plantFood.collectPlantFood(gameSession, 4, ROW));
        assertEquals(1, gameSession.getPlantFoodCount(), "one drop is worth exactly one");
    }

    // ---- going stale -----------------------------------------------------------------------------

    @Test
    void anUncollectedPickupExpiresAndIsSweptAway() {
        GameSession gameSession = session();
        doomed(gameSession, 4.4, true);
        killTick(gameSession);

        PlantFoodSystem plantFood = new PlantFoodSystem();
        List<Result> fizzle = null;
        for (int tick = 0; tick <= PlantFood.EXPIRE_TICKS + 5; tick++) {
            List<Result> said = plantFood.onTick(gameSession);
            if (!said.isEmpty()) {
                fizzle = said;
                break;
            }
        }

        assertNotNull(fizzle, "an ignored pickup must eventually go stale and say so");
        assertTrue(gameSession.getActivePlantFoods().isEmpty());
        assertEquals(0, gameSession.getPlantFoodCount(),
                "a pickup nobody reached pays out nothing");
    }

    // It has to last long enough to be worth crossing the lawn for. A regression that made this a
    // second or two would still pass every other test here.
    @Test
    void itLastsLongEnoughToBeWorthCrossingTheLawnFor() {
        GameSession gameSession = session();
        doomed(gameSession, 4.4, true);
        killTick(gameSession);

        PlantFoodSystem plantFood = new PlantFoodSystem();
        for (int tick = 0; tick < 5 * Constants.TICKS_PER_SECOND; tick++) {
            plantFood.onTick(gameSession);
        }

        assertEquals(1, gameSession.getActivePlantFoods().size(),
                "five seconds in, the drop must still be there to be grabbed");
    }

    // Collected is not expired: the sweep announces a LOST pickup, and announcing a collected one
    // would read as a second plant food the player never got.
    @Test
    void aCollectedPickupIsNotAnnouncedAsLost() {
        GameSession gameSession = session();
        doomed(gameSession, 4.4, true);
        killTick(gameSession);

        PlantFoodSystem plantFood = new PlantFoodSystem();
        plantFood.collectPlantFood(gameSession, 4, ROW);

        for (int tick = 0; tick <= PlantFood.EXPIRE_TICKS + 5; tick++) {
            assertTrue(plantFood.onTick(gameSession).isEmpty(),
                    "nothing is left to announce once it has been taken");
        }
    }

    // ---- it must land on a tile the collect command will accept ----------------------------------

    // A zombie dies at a continuous x that runs from past the house to off the far edge, and either end
    // names a tile CollectPlantFoodCommand refuses outright -- so an unclamped drop would sit there,
    // drawn and clickable, answering every click with "invalid coordinates".
    @Test
    void aDropPastTheHouseIsStillOnTheBoard() {
        GameSession gameSession = session();
        doomed(gameSession, -0.4, true);
        killTick(gameSession);

        PlantFood dropped = gameSession.getActivePlantFoods().get(0);
        assertEquals(0, dropped.tileColumn(), "clamped onto the first column, not off the left edge");
        assertTrue(new PlantFoodSystem().collectPlantFood(
                gameSession, dropped.tileColumn(), dropped.tileRow()));
    }

    @Test
    void aDropAtTheSpawnEdgeIsStillOnTheBoard() {
        GameSession gameSession = session();
        // Zombies spawn at Constants.ZOMBIE_SPAWN_X, which is off the right-hand edge of the board.
        doomed(gameSession, Constants.ZOMBIE_SPAWN_X, true);
        killTick(gameSession);

        PlantFood dropped = gameSession.getActivePlantFoods().get(0);
        assertEquals(Constants.BOARD_COLS - 1, dropped.tileColumn());
        assertTrue(new PlantFoodSystem().collectPlantFood(
                gameSession, dropped.tileColumn(), dropped.tileRow()));
    }
}
