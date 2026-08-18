package models.greenhouse;

import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Guards the Phase 1 -> Phase 2 greenhouse: twelve pots on a 0-based 3x4 grid, and every Phase 1 save
// reshaped without losing anybody's plants.
//
// The migration is worth pinning because its failure mode is silent. Gson writes whatever the save file
// holds straight past the constructor, so a Phase 1 profile arrives carrying a 4x5 array while the class
// believes it has a 3x4 one. Nothing throws: reads inside the smaller bounds keep working, the surplus
// pots quietly become unreachable, and isFull() counts a different grid from the one it is looking at.
class GreenHouseTest {

    private static final Gson GSON = new Gson();

    @Test
    @DisplayName("a fresh greenhouse is 3x4, with only the first row unlocked")
    void freshGreenhouseShape() {
        GreenHouse greenhouse = new GreenHouse();
        assertEquals(3, greenhouse.getRows());
        assertEquals(4, greenhouse.getCols());
        assertEquals(4, greenhouse.getUnlockedPots().size());

        for (int x = 0; x < 4; x++) {
            assertTrue(greenhouse.getPot(x, 0).isEmpty(), "row 0 col " + x + " should ship unlocked");
            assertTrue(greenhouse.getPot(x, 1).isLocked(), "row 1 col " + x + " should ship locked");
            assertTrue(greenhouse.getPot(x, 2).isLocked(), "row 2 col " + x + " should ship locked");
        }
    }

    @Test
    @DisplayName("coordinates are 0-based, and the far corner is (3, 2)")
    void coordinatesAreZeroBased() {
        GreenHouse greenhouse = new GreenHouse();
        assertTrue(greenhouse.isValidCoordinate(0, 0), "(0, 0) is the top-left pot");
        assertTrue(greenhouse.isValidCoordinate(3, 2), "(3, 2) is the bottom-right pot");
        // The old 1-based contract accepted these two and rejected (0, 0), which is exactly the
        // inconsistency this replaces.
        assertFalse(greenhouse.isValidCoordinate(4, 3));
        assertFalse(greenhouse.isValidCoordinate(-1, 0));
        assertFalse(greenhouse.isValidCoordinate(0, 3));
    }

    @Test
    @DisplayName("getPot is transposed: x is the column, y is the row")
    void getPotIsTransposed() {
        GreenHouse greenhouse = new GreenHouse();
        Pot pot = greenhouse.getPot(3, 2);
        assertEquals(3, pot.getX());
        assertEquals(2, pot.getY());
    }

    @Test
    @DisplayName("a Phase 1 save reshapes to 3x4, keeping the first twelve pots in reading order")
    void legacySaveMigrates() {
        GreenHouse greenhouse = GSON.fromJson(phaseOneSave(), GreenHouse.class);

        assertEquals(3, greenhouse.getRows());
        assertEquals(4, greenhouse.getCols());

        // Reading order of the legacy 4x5 grid was: E G E E E | E E L L L | L*5 | L*5.
        // The first twelve of those land row by row in the new grid.
        assertTrue(greenhouse.getPot(0, 0).isEmpty());
        assertEquals(PotState.GROWING, greenhouse.getPot(1, 0).getState());
        assertTrue(greenhouse.getPot(2, 0).isEmpty());
        assertTrue(greenhouse.getPot(3, 0).isEmpty());

        assertTrue(greenhouse.getPot(0, 1).isEmpty());
        assertTrue(greenhouse.getPot(1, 1).isEmpty());
        assertTrue(greenhouse.getPot(2, 1).isEmpty());
        assertTrue(greenhouse.getPot(3, 1).isLocked());

        for (int x = 0; x < 4; x++) {
            assertTrue(greenhouse.getPot(x, 2).isLocked(), "row 2 col " + x);
        }
        assertEquals(7, greenhouse.getUnlockedPots().size(), "seven pots were unlocked before");
    }

    @Test
    @DisplayName("a migrated pot keeps its plant and its timer, and is told its new coordinates")
    void legacyPlantSurvivesMigration() {
        GreenHouse greenhouse = GSON.fromJson(phaseOneSave(), GreenHouse.class);

        Pot growing = greenhouse.getPot(1, 0);
        assertNotNull(growing.getOnPot(), "the plant must come across, not be discarded");
        assertEquals("Marigold", growing.getOnPot().getName());
        assertEquals(4102444800000L, growing.getReadyAtTimestamp(), "its timer must come across too");

        // Rewritten, because a carried pot may land in a different column from the one it was saved in.
        assertEquals(1, growing.getX());
        assertEquals(0, growing.getY());
    }

    @Test
    @DisplayName("every pot's stored coordinates match where it now sits")
    void migratedCoordinatesAreConsistent() {
        GreenHouse greenhouse = GSON.fromJson(phaseOneSave(), GreenHouse.class);
        for (int y = 0; y < greenhouse.getRows(); y++) {
            for (int x = 0; x < greenhouse.getCols(); x++) {
                Pot pot = greenhouse.getPot(x, y);
                assertEquals(x, pot.getX(), "pot at column " + x + " row " + y);
                assertEquals(y, pot.getY(), "pot at column " + x + " row " + y);
            }
        }
    }

    @Test
    @DisplayName("a save with no pots at all still comes back as a usable greenhouse")
    void emptySaveIsRepaired() {
        GreenHouse greenhouse = GSON.fromJson("{}", GreenHouse.class);
        assertEquals(4, greenhouse.getUnlockedPots().size());
        assertNotNull(greenhouse.getPot(3, 2));
    }

    @Test
    @DisplayName("unlockNextPot walks reading order and isFull stops at twelve")
    void unlockingFillsTheGrid() {
        GreenHouse greenhouse = new GreenHouse();
        assertFalse(greenhouse.isFull());

        Pot first = greenhouse.unlockNextPot();
        assertEquals(0, first.getX(), "the next locked pot is row 1's first column");
        assertEquals(1, first.getY());

        for (int i = 0; i < 20 && !greenhouse.isFull(); i++) {
            greenhouse.unlockNextPot();
        }
        assertTrue(greenhouse.isFull());
        assertEquals(12, greenhouse.getUnlockedPots().size());
        // Nothing left to open, and asking again must not throw.
        assertEquals(null, greenhouse.unlockNextPot());
    }

    // A Phase 1 greenhouse exactly as Gson wrote it: four rows of five, seven pots unlocked, one of them
    // growing a Marigold. The timestamp is a fixed far-future value so the plant never ripens mid-test.
    private static String phaseOneSave() {
        StringBuilder json = new StringBuilder("{\"pots\":[");
        String[][] states = {
            {"EMPTY", "GROWING", "EMPTY", "EMPTY", "EMPTY"},
            {"EMPTY", "EMPTY", "LOCKED", "LOCKED", "LOCKED"},
            {"LOCKED", "LOCKED", "LOCKED", "LOCKED", "LOCKED"},
            {"LOCKED", "LOCKED", "LOCKED", "LOCKED", "LOCKED"},
        };
        for (int y = 0; y < states.length; y++) {
            json.append(y == 0 ? "[" : ",[");
            for (int x = 0; x < states[y].length; x++) {
                boolean growing = "GROWING".equals(states[y][x]);
                json.append(x == 0 ? "" : ",")
                        .append("{\"x\":").append(x)
                        .append(",\"y\":").append(y)
                        .append(",\"state\":\"").append(states[y][x]).append('"')
                        .append(",\"readyAtTimestamp\":").append(growing ? "4102444800000" : "0");
                if (growing) {
                    json.append(",\"onPot\":{\"name\":\"Marigold\",\"isMarigold\":true,")
                            .append("\"growthDuration\":7200000}");
                }
                json.append('}');
            }
            json.append(']');
        }
        return json.append("]}").toString();
    }
}
