package factories;

import models.entities.collectibles.Collectibles;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.greenhouse.GreenHouse;
import models.user.Profile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.Constants;
import utils.gameinitializers.GameInitializer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The loot roll moved from the death handler to the spawn, so the board can mark a zombie that is
// carrying something while it is still walking. The odds and the draw are supposed to be untouched by
// that move, and "supposed to be" is exactly the kind of claim that quietly stops being true.
//
// Everything here drives the real ZombieFactory against real template data.
class CarriedDropTest {

    private static final String LEVEL_ID = "s1l1";
    private static final int SAMPLE = 4000;

    @BeforeAll
    static void loadGameData() {
        new GameInitializer().loadAllData();
    }

    private static GameSession session() {
        return new GameSession(new Profile(), LevelFactory.createLevel(LEVEL_ID));
    }

    private static Zombie spawn(GameSession gameSession) {
        return ZombieFactory.createZombie("ZombieDefault", 8, 2, gameSession);
    }

    @Test
    void aboutOneZombieInTenWalksOnCarryingSomething() {
        GameSession gameSession = session();
        int carriers = 0;
        for (int i = 0; i < SAMPLE; i++) {
            if (spawn(gameSession).getCarriedDrop() != null) {
                carriers++;
            }
        }
        double rate = carriers / (double) SAMPLE;
        double expected = Constants.ZOMBIE_DROP_PROBABILITY;
        // A wide band on purpose: this is a real random draw and the point is that the rate did not
        // move to zero or to one when the roll changed hands, not that it hits the constant exactly.
        assertTrue(Math.abs(rate - expected) < expected / 2.0,
                "carried-drop rate was " + rate + ", expected about " + expected);
    }

    @Test
    void allThreeKindsAreDrawnWhileTheGreenhouseHasRoom() {
        GameSession gameSession = session();
        boolean coin = false;
        boolean gem = false;
        boolean pot = false;
        for (int i = 0; i < SAMPLE; i++) {
            Collectibles carried = spawn(gameSession).getCarriedDrop();
            coin |= carried == Collectibles.COIN;
            gem |= carried == Collectibles.GEM;
            pot |= carried == Collectibles.POT;
        }
        assertTrue(coin && gem && pot,
                "every kind must still be reachable -- coin=" + coin + " gem=" + gem + " pot=" + pot);
    }

    // The reason the pool is rebuilt per roll rather than cached: a pot in a full greenhouse would
    // swallow a third of every drop and decay the promised rate.
    @Test
    void aFullGreenhouseIsNeverOfferedAPot() {
        GameSession gameSession = session();
        GreenHouse greenHouse = gameSession.getPlayer().getMyGreenHouse();
        while (!greenHouse.isFull()) {
            greenHouse.unlockNextPot();
        }
        for (int i = 0; i < SAMPLE; i++) {
            assertNotEquals(Collectibles.POT, spawn(gameSession).getCarriedDrop(),
                    "a full greenhouse must not be offered a pot");
        }
    }

    @Test
    void carriesSomethingCoversBothRewards() {
        GameSession gameSession = session();
        Zombie zombie = spawn(gameSession);
        zombie.setGlowing(false);
        zombie.setCarriedDrop(null);
        assertFalse(zombie.carriesSomething(), "a plain zombie carries nothing");

        zombie.setGlowing(true);
        assertTrue(zombie.carriesSomething(), "a glowing zombie carries plant food");

        zombie.setGlowing(false);
        zombie.setCarriedDrop(Collectibles.GEM);
        assertTrue(zombie.carriesSomething(), "a loot carrier counts too");
    }
}
