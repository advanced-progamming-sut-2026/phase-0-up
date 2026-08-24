package views.gdx.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.regex.InGameRegex;
import views.gdx.map.GridPos;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Guards the seam between clicking and the game.
//
// A click is turned into the same text the terminal accepts, then dispatched by GameEngine against
// InGameRegex. That dispatch is a silent one: a string that matches no pattern is not an error, it is
// simply ignored -- so a stray space, a missing "-t", or "(3,2)" instead of "(3, 2)" would make the
// mouse stop working with nothing logged and nothing thrown.
//
// These tests hold the two halves against each other, so the format can only break loudly.
class CommandBridgeTest {

    private final List<String> sent = new ArrayList<>();
    private final CommandBridge bridge = new CommandBridge(command -> {
        sent.add(command);
        return true;
    });

    private String lastCommand() {
        assertEquals(1, sent.size(), "expected exactly one command to be sent");
        return sent.get(0);
    }

    @Test
    @DisplayName("planting produces a string the engine's own pattern accepts")
    void plantMatchesTheEnginePattern() {
        bridge.plant("Peashooter", new GridPos(3, 2));

        String command = lastCommand();
        assertTrue(InGameRegex.PLANT_SEED.matches(command), "engine would ignore: " + command);
        assertEquals("Peashooter", InGameRegex.PLANT_SEED.getGroup(command, "type"));
        assertEquals("3", InGameRegex.PLANT_SEED.getGroup(command, "x"));
        assertEquals("2", InGameRegex.PLANT_SEED.getGroup(command, "y"));
    }

    @Test
    @DisplayName("a two-word plant name survives the round trip")
    void multiWordPlantNameSurvives() {
        // The type group is non-greedy up to " -l", so a space in the name is fine -- but only as long
        // as the location flag is still there to stop it. Worth pinning: half the roster is two words.
        bridge.plant("Snow Pea", new GridPos(0, 4));

        String command = lastCommand();
        assertTrue(InGameRegex.PLANT_SEED.matches(command), "engine would ignore: " + command);
        assertEquals("Snow Pea", InGameRegex.PLANT_SEED.getGroup(command, "type"));
    }

    @Test
    @DisplayName("shovel, plant food and sun all match their patterns")
    void otherVerbsMatchTheirPatterns() {
        bridge.pluck(new GridPos(1, 0));
        assertTrue(InGameRegex.PLUCK_PLANT.matches(sent.get(0)), sent.get(0));

        sent.clear();
        bridge.feed(new GridPos(8, 4));
        assertTrue(InGameRegex.FEED_PLANT.matches(sent.get(0)), sent.get(0));

        sent.clear();
        bridge.collectSun(new GridPos(4, 3));
        assertTrue(InGameRegex.COLLECT_SUN.matches(sent.get(0)), sent.get(0));
    }

    @Test
    @DisplayName("clicks off the lawn never reach the engine")
    void offBoardClicksAreDropped() {
        // cellAt() returns an out-of-range position rather than null when the cursor is off the board,
        // so this is the guard that stops a click on the background becoming "plant at (-1, 7)".
        assertFalse(bridge.plant("Peashooter", new GridPos(-1, 2)));
        assertFalse(bridge.plant("Peashooter", new GridPos(9, 2)));
        assertFalse(bridge.plant("Peashooter", new GridPos(3, 5)));
        assertFalse(bridge.pluck(new GridPos(0, -1)));
        assertTrue(sent.isEmpty(), "nothing off the lawn should have been sent: " + sent);
    }

    @Test
    @DisplayName("a Beghouled swap names the two tiles x-first, in the order the pattern expects")
    void swapMatchesTheEnginePattern() {
        // THE trap this whole file exists for, and the one the roadmap has flagged since Phase 0.
        // Three orderings meet at this command and two disagree: the command and GameSession take x
        // first, BeghouledMode.swap takes ROW first, and GameSession.swapPlants is the one that
        // transposes between them. A bridge that transposed as well would silently swap the mirrored
        // cell -- which on a nearly square board usually still looks plausible.
        //
        // So the assertion is on the exact groups, not just on "it matches": a transposed pair matches
        // the pattern perfectly well.
        bridge.swap(new GridPos(3, 1), new GridPos(4, 1));

        String command = lastCommand();
        assertTrue(InGameRegex.SWAP_PLANTS.matches(command), "engine would ignore: " + command);
        assertEquals("3", InGameRegex.SWAP_PLANTS.getGroup(command, "x1"));
        assertEquals("1", InGameRegex.SWAP_PLANTS.getGroup(command, "y1"));
        assertEquals("4", InGameRegex.SWAP_PLANTS.getGroup(command, "x2"));
        assertEquals("1", InGameRegex.SWAP_PLANTS.getGroup(command, "y2"));
    }

    @Test
    @DisplayName("a swap with an off-board end is dropped, and an upgrade names its plant")
    void swapAndUpgradeGuards() {
        assertFalse(bridge.swap(new GridPos(0, 0), new GridPos(-1, 0)));
        assertFalse(bridge.swap(null, new GridPos(0, 0)));
        assertTrue(sent.isEmpty(), "nothing off the lawn should have been sent: " + sent);

        bridge.upgrade("Cabbage-pult");
        String command = lastCommand();
        assertTrue(InGameRegex.UPGRADE_PLANT.matches(command), "engine would ignore: " + command);
        // Two-word upgrade targets exist (Mega Gatling Pea, Winter Melon), and the -t group is greedy
        // to the end of the line, so the name must survive whole.
        assertEquals("Cabbage-pult", InGameRegex.UPGRADE_PLANT.getGroup(command, "type"));

        sent.clear();
        assertFalse(bridge.upgrade("  "));
        assertTrue(sent.isEmpty());
    }

    @Test
    @DisplayName("a blank plant name is refused rather than sent")
    void blankPlantNameIsRefused() {
        // An unarmed cursor has no seed name. Sending "plant plant -t  -l (3, 2)" would match nothing
        // anyway, but refusing here keeps the reason visible at the call site.
        assertFalse(bridge.plant(null, new GridPos(3, 2)));
        assertFalse(bridge.plant("   ", new GridPos(3, 2)));
        assertTrue(sent.isEmpty());
    }
}
