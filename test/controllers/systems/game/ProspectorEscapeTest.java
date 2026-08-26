package controllers.systems.game;

import factories.LevelFactory;
import factories.ZombieFactory;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.game.gamemodes.StandardMode;
import models.user.Profile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.Constants;
import utils.Result;
import utils.gameinitializers.GameInitializer;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The Prospector blows itself back to column 0 with its own dynamite and then walks RIGHT for the rest
// of the level. Two things followed from that and neither was handled:
//
//   1. It is travelling right while not being hypnotised, which is the only case the view's "which way
//      is it facing" test used to cover -- so it moonwalked the length of the lawn.
//   2. Walking off the far edge left it in the row, alive, forever. checkWin counts living zombies in
//      rows, so the level could not be finished.
//
// Reported from play, and neither is visible in a still frame -- the first is motion and the second is
// the absence of an ending.
class ProspectorEscapeTest {

    private static final String LEVEL_ID = "s1l1";
    private static final int ROW = 2;
    // CarryADynamite waits ten seconds before it goes off.
    private static final int FUSE_TICKS = 10 * Constants.TICKS_PER_SECOND;

    @BeforeAll
    static void loadGameData() {
        new GameInitializer().loadAllData();
    }

    private static GameSession session() {
        return new GameSession(new Profile(), LevelFactory.createLevel(LEVEL_ID));
    }

    private static Zombie prospector(GameSession gameSession) {
        Zombie zombie = ZombieFactory.createZombie("ZombieProspector", 8, ROW, gameSession);
        gameSession.getMap().getRow(ROW).getZombies().add(zombie);
        return zombie;
    }

    @Test
    void aProspectorWalksLeftUntilItsDynamiteGoesOff() {
        GameSession gameSession = session();
        Zombie zombie = prospector(gameSession);
        assertFalse(zombie.getMovement().isMovingRight(),
                "a fresh Prospector heads for the house like anything else");
    }

    @Test
    void theBlastTurnsItRoundAndTheViewCanTell() {
        GameSession gameSession = session();
        Zombie zombie = prospector(gameSession);
        CombatSystem combat = new CombatSystem(new Random(7));

        for (long tick = 1; tick <= FUSE_TICKS + 5; tick++) {
            combat.processTick(gameSession, tick);
        }

        assertTrue(zombie.getMovement().isMovingRight(),
                "after the blast the Prospector is walking back across the lawn");
    }

    @Test
    void itLeavesTheBoardAtTheFarEdgeSoTheLevelCanEnd() {
        GameSession gameSession = session();
        Zombie zombie = prospector(gameSession);
        CombatSystem combat = new CombatSystem(new Random(7));

        boolean announced = false;
        // Long enough for the fuse plus the whole walk back: ~3.7s per cell at the default speed knob.
        for (long tick = 1; tick <= FUSE_TICKS + 1200; tick++) {
            List<Result> events = combat.processTick(gameSession, tick);
            for (Result event : events) {
                if (event.message().contains("wanders off the far end")) {
                    announced = true;
                }
            }
            if (gameSession.getMap().getRow(ROW).getZombies().isEmpty()) {
                break;
            }
        }

        assertTrue(gameSession.getMap().getRow(ROW).getZombies().isEmpty(),
                "a Prospector past the far edge must leave the board -- it was at x="
                        + zombie.getMovement().getPositionX());
        assertTrue(announced, "leaving is worth a sentence; the player has to know where it went");
        assertTrue(new StandardMode().checkWin(gameSession) || gameSession.getCurrentWave() == 0,
                "with the board clear, nothing left over is holding the level open");
    }

    // The other two zombies that are not walking the ordinary way. Both go through the same rule, which
    // is the point of deriving it from the arithmetic move() uses rather than from a list of cases.
    @Test
    void aHypnotisedZombieFacesRightAndAStalledOneDoesNot() {
        GameSession gameSession = session();
        Zombie zombie = ZombieFactory.createZombie("ZombieDefault", 8, ROW, gameSession);

        assertFalse(zombie.getMovement().isMovingRight(), "an ordinary zombie heads for the house");

        zombie.getState().setHypnotized(true);
        assertTrue(zombie.getMovement().isMovingRight(),
                "a charmed zombie has turned round and fights the other way");

        zombie.getState().setHypnotized(false);
        zombie.getMovement().setSpeed(0);
        assertFalse(zombie.getMovement().isMovingRight(),
                "a zombie held still (a Fisherman reeling in) keeps facing the house");
    }

    // The threshold has to sit beyond the spawn point or every zombie is deleted on the tick it
    // walks on -- zombies enter at x = 9.5, which is already past the last column.
    @Test
    void aFreshlySpawnedZombieIsNotMistakenForOneLeaving() {
        GameSession gameSession = session();
        Zombie zombie = ZombieFactory.createZombie("ZombieDefault", 8, ROW, gameSession);
        zombie.getMovement().setPositionX(Constants.ZOMBIE_SPAWN_X);
        gameSession.getMap().getRow(ROW).getZombies().add(zombie);

        new CombatSystem(new Random(7)).processTick(gameSession, 1L);

        assertFalse(gameSession.getMap().getRow(ROW).getZombies().isEmpty(),
                "a zombie standing on the spawn point has not left the lawn");
    }
}
