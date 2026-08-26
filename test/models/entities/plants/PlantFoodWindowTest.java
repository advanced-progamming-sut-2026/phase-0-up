package models.entities.plants;

import factories.LevelFactory;
import factories.PlantFactory;
import models.game.GameSession;
import models.game.Level;
import models.user.Profile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.Constants;
import utils.gameinitializers.GameInitializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// How long a plant-food boost lasts -- the one number the view has to draw the glow and the plant-food
// animation against.
//
// The report this came from: "the plantfood animation ends before the bullets end on snow pea". It did.
// The view ran the animation for a guessed two seconds with a three-and-a-half second ceiling, while
// the boost itself is sixty shots two ticks apart -- twelve seconds at the model's fixed 10 Hz. The
// plant stopped glowing with three quarters of its boost still to fire.
//
// The fix moved the answer here, so the view asks instead of guessing. That makes these assertions the
// thing holding the two halves together: if isPlantFoodActive() stops being true for the whole of a
// boost, the glow goes back to ending early and nothing else will say so. A screenshot cannot -- a
// still frame shows a glowing plant or an unglowing one and says nothing about what the shots were
// doing at that instant.
class PlantFoodWindowTest {

    private static final String LEVEL_ID = "s1l1";
    private static final int ROW = 2;
    private static final int COL = 2;

    // The floor Plant.triggerPlantFood opens for every feed, so a boost that lands instantly still gets
    // a whole animation rather than a single frame of one.
    private static final int FLOOR_TICKS = 2 * Constants.TICKS_PER_SECOND;

    @BeforeAll
    static void loadGameData() {
        new GameInitializer().loadAllData();
    }

    private static GameSession session() {
        Level level = LevelFactory.createLevel(LEVEL_ID);
        return new GameSession(new Profile(), level);
    }

    private static Plant planted(GameSession gameSession, String name) {
        Plant plant = PlantFactory.createPlant(name, 1, COL, ROW);
        assertNotNull(plant, name + " must exist in plants.json");
        gameSession.getMap().getRow(ROW).cellAt(COL).addPlant(plant);
        return plant;
    }

    // Ticks the plant until the boost reports itself over, returning how many ticks that took.
    // Capped well above any real boost so a stuck window fails as a number rather than as a hang.
    private static int ticksUntilBoostEnds(GameSession gameSession, Plant plant) {
        for (int tick = 1; tick <= 2000; tick++) {
            plant.update(gameSession);
            if (!plant.isPlantFoodActive()) {
                return tick;
            }
        }
        return -1;
    }

    // ---- the report ------------------------------------------------------------------------------

    @Test
    void aSnowPeaStaysBoostedForTheWholeOfItsBurst() {
        GameSession gameSession = session();
        Plant snowPea = planted(gameSession, "Snow Pea");

        snowPea.triggerPlantFood(gameSession);
        int ticks = ticksUntilBoostEnds(gameSession, snowPea);

        // The exact length is the burst's business, not this test's. What matters is that it is far
        // past the 3.5s ceiling the view used to impose -- that gap IS the bug.
        assertTrue(ticks > 5 * Constants.TICKS_PER_SECOND,
                "a 60-shot burst must report itself running for much longer than the old 3.5s ceiling,"
                        + " but stopped after " + ticks + " ticks");
    }

    // The other half of "aligned": it has to STOP. An always-on window would pass the test above and
    // leave every fed plant glowing for the rest of the level.
    @Test
    void andStopsWhenTheBurstRunsOut() {
        GameSession gameSession = session();
        Plant snowPea = planted(gameSession, "Snow Pea");

        snowPea.triggerPlantFood(gameSession);

        assertTrue(ticksUntilBoostEnds(gameSession, snowPea) > 0,
                "the boost window must close on its own");
        assertFalse(snowPea.isPlantFoodActive());
    }

    @Test
    void anUnfedPlantIsNeverBoosted() {
        GameSession gameSession = session();
        Plant snowPea = planted(gameSession, "Snow Pea");

        for (int tick = 0; tick < 50; tick++) {
            snowPea.update(gameSession);
            assertFalse(snowPea.isPlantFoodActive());
        }
    }

    // ---- every plant, not just shooters ----------------------------------------------------------

    // The window used to be read off ShootProjectileAbility alone, so a plant whose boost lives on any
    // other ability reported "over" the instant it was fed -- one frame of animation and no glow.
    @Test
    void aThreepeaterIsBoostedToo() {
        GameSession gameSession = session();
        Plant threepeater = planted(gameSession, "Threepeater");

        threepeater.triggerPlantFood(gameSession);
        for (int tick = 0; tick < FLOOR_TICKS + 5; tick++) {
            threepeater.update(gameSession);
        }

        assertTrue(threepeater.isPlantFoodActive(),
                "Threepeater's burst lives on MultiLaneShootAbility and must count");
    }

    @Test
    void aRotobagaIsBoostedToo() {
        GameSession gameSession = session();
        Plant rotobaga = planted(gameSession, "Rotobaga");

        rotobaga.triggerPlantFood(gameSession);
        for (int tick = 0; tick < FLOOR_TICKS + 5; tick++) {
            rotobaga.update(gameSession);
        }

        assertTrue(rotobaga.isPlantFoodActive(),
                "Rotobaga's burst lives on MultiDirectionalShootAbility and must count");
    }

    @Test
    void aBonkChoyIsBoostedForItsWholeFlurry() {
        GameSession gameSession = session();
        Plant bonkChoy = planted(gameSession, "Bonk Choy");

        bonkChoy.triggerPlantFood(gameSession);
        for (int tick = 0; tick < FLOOR_TICKS + 5; tick++) {
            bonkChoy.update(gameSession);
        }

        assertTrue(bonkChoy.isPlantFoodActive(),
                "a 50-tick melee flurry outlasts the floor and must hold the window open");
    }

    // ---- boosts with nothing left to run ---------------------------------------------------------

    // A Wall-nut's plant food is armour, granted and finished in the same instant. There is no queue to
    // ask, so the floor is the whole window -- without it the animation would be one frame long.
    @Test
    void anInstantBoostStillGetsTheFloor() {
        GameSession gameSession = session();
        Plant wallnut = planted(gameSession, "Wall-nut");

        wallnut.triggerPlantFood(gameSession);

        assertTrue(wallnut.isPlantFoodActive(), "the floor opens on the feed itself");
        assertEquals(FLOOR_TICKS, ticksUntilBoostEnds(gameSession, wallnut),
                "an instant boost lasts exactly the floor and not a tick more");
    }

    // The floor is a floor, not a length: a boost that outlives it must not be cut back to it.
    @Test
    void theFloorNeverShortensARealBoost() {
        GameSession gameSession = session();
        Plant snowPea = planted(gameSession, "Snow Pea");

        snowPea.triggerPlantFood(gameSession);
        for (int tick = 0; tick < FLOOR_TICKS + 1; tick++) {
            snowPea.update(gameSession);
        }

        assertTrue(snowPea.isPlantFoodActive(),
                "the burst is still firing, so the window must still be open past the floor");
    }

    // ---- an ordinary volley is not a boost -------------------------------------------------------

    // Repeater fires two peas per cycle through the same counter a plant-food burst uses. If the window
    // did not tell them apart, an unfed Repeater would glow every time it shot.
    @Test
    void anOrdinaryVolleyIsNotAPlantFoodBoost() {
        GameSession gameSession = session();
        Plant repeater = planted(gameSession, "Repeater");
        gameSession.getMap().getRow(ROW).getZombies().add(
                factories.ZombieFactory.createZombie("ZombieDefault", 7.0, ROW, gameSession));

        boolean everBoosted = false;
        for (int tick = 0; tick < 120; tick++) {
            repeater.update(gameSession);
            everBoosted |= repeater.isPlantFoodActive();
        }

        assertFalse(everBoosted, "shooting normally is not being under plant food");
    }

    // ---- feeding twice ---------------------------------------------------------------------------

    // hasPlantFood() is set once and never cleared, so it cannot describe a second feed. The count can,
    // and the view watches it -- otherwise a plant fed again mid-level got the boost silently, with no
    // animation and no glow. Mega Gatling Pea's level-3 upgrade feeds itself over and over.
    @Test
    void eachFeedIsCountedSoTheViewCanSeeTheSecondOne() {
        GameSession gameSession = session();
        Plant snowPea = planted(gameSession, "Snow Pea");

        assertEquals(0, snowPea.getPlantFoodFeeds());

        snowPea.triggerPlantFood(gameSession);
        assertEquals(1, snowPea.getPlantFoodFeeds());

        snowPea.triggerPlantFood(gameSession);
        assertEquals(2, snowPea.getPlantFoodFeeds());
        assertTrue(snowPea.hasPlantFood());
    }

    // A second feed reopens the window even if the first had already run out.
    @Test
    void aSecondFeedReopensTheWindow() {
        GameSession gameSession = session();
        Plant wallnut = planted(gameSession, "Wall-nut");

        wallnut.triggerPlantFood(gameSession);
        assertTrue(ticksUntilBoostEnds(gameSession, wallnut) > 0);
        assertFalse(wallnut.isPlantFoodActive());

        wallnut.triggerPlantFood(gameSession);

        assertTrue(wallnut.isPlantFoodActive());
    }
}
