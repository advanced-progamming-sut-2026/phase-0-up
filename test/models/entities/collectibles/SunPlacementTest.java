package models.entities.collectibles;

import models.entities.plants.Plant;
import models.game.GameSession;
import models.game.Level;
import models.game.gamemodes.StandardMode;
import models.user.Profile;
import factories.PlantFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.Constants;
import utils.gameinitializers.GameInitializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A sun has to land somewhere the player can name.
//
// "collect sun -l (x, y)" addresses a TILE, and CollectSunCommand refuses a coordinate outside the
// board before it looks at anything. So a sun that comes to rest at x = -0.25 is not slightly
// misplaced -- it is permanently uncollectable, while being drawn, clickable, and worth its full
// value. That is a bug with no symptom except a player clicking the same spot over and over.
//
// Three abilities scatter suns with a random offset around their owner, and two of them have shipped
// this exact bug: ProduceSunAbility (fixed in place, with the reason written above the line) and
// InstantSunBurstAbility, found much later when a Gold Bloom was planted in column 0 during a scoring
// run. Random offsets mean it only bites on the edge columns and only on some rolls, which is why it
// survived so long. These tests pin the invariant rather than either offset.
class SunPlacementTest {

    @BeforeAll
    static void loadGameData() {
        new GameInitializer().loadAllData();
    }

    private static GameSession session() {
        Level level = new Level(new models.game.Wave[0], null, new StandardMode(), 0,
                new java.util.ArrayList<>(), 0, Constants.DEFAULT_SEED_SLOTS, null);
        return new GameSession(new Profile(), level);
    }

    @Test
    @DisplayName("a sun is never built off the board, whatever it is handed")
    void restingPositionIsClampedOntoTheBoard() {
        Sun tooFarLeft = new Sun(-0.25, -4d, -0.25, SunType.NORMAL, 375, true, 100);
        assertEquals(0, tooFarLeft.tileColumn(), "column -1 is refused by CollectSunCommand outright");
        assertTrue(tooFarLeft.tileRow() >= 0);

        // A sun already at rest: startY IS its resting height, which is how every plant-made sun is
        // built, so both have to be clamped -- tileRow reads `y`, and `y` comes from startY.
        Sun tooFarRight = new Sun(Constants.BOARD_COLS + 3d, Constants.BOARD_ROWS + 3d,
                Constants.BOARD_ROWS + 3d, SunType.NORMAL, 25, false, 100);
        assertEquals(Constants.BOARD_COLS - 1, tooFarRight.tileColumn());
        assertEquals(Constants.BOARD_ROWS - 1, tooFarRight.tileRow());
    }

    @Test
    @DisplayName("a sky sun still starts above the board -- only where it LANDS is clamped")
    void theFallIsNotClampedAway() {
        Sun dropping = new Sun(3.5, -4.5, 2.6, SunType.NORMAL, 25, true, 100);
        assertEquals(-4.5, dropping.getCurrentY(), 0.001,
                "clamping the descent would delete the fall, which is the whole animation");
        assertEquals(2.6, dropping.getTargetY(), 0.001);
    }

    @Test
    @DisplayName("Gold Bloom in column 0 drops sun the player can actually collect")
    void goldBloomInTheFirstColumnIsCollectable() {
        // The reported case, and it needs repeating: the offset is random, so a single placement
        // proves nothing. Every roll has to land somewhere nameable.
        for (int attempt = 0; attempt < 200; attempt++) {
            GameSession session = session();
            Plant goldBloom = PlantFactory.createPlant("Gold Bloom", 1, 0, 0);
            session.getMap().getCell(0, 0).addPlant(goldBloom);
            goldBloom.update(session);

            assertFalse(session.getActiveSuns().isEmpty(), "Gold Bloom's whole purpose is the payout");
            for (Sun sun : session.getActiveSuns()) {
                assertTrue(sun.tileColumn() >= 0 && sun.tileColumn() < Constants.BOARD_COLS,
                        "attempt " + attempt + " landed in column " + sun.tileColumn()
                                + " (x = " + sun.getX() + "), which no command can name");
                assertTrue(sun.tileRow() >= 0 && sun.tileRow() < Constants.BOARD_ROWS,
                        "attempt " + attempt + " landed in row " + sun.tileRow());
            }
        }
    }
}
