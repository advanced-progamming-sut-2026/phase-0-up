package views.gdx.input;

import com.badlogic.gdx.Input;
import models.game.gamemodes.VersusIZombieMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.Constants;
import utils.regex.InGameRegex;
import views.gdx.bridge.CommandBridge;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The couch player's half of the keyboard.
//
// Runs with no window and no GL context, which is the point of CommandBridge taking a sink: what this
// controller produces is a STRING, and a string can be asserted. The alternative would be driving a
// real board through a real renderer to find out whether W goes up.
class KeyboardZombieControllerTest {

    private final List<String> sent = new ArrayList<>();
    private VersusIZombieMode mode;
    private KeyboardZombieController keyboard;

    @BeforeEach
    void build() {
        sent.clear();
        // No session and no registries: the roster is a constant on the mode, built in its
        // constructor precisely so it can be read before anything has started.
        mode = new VersusIZombieMode();
        keyboard = new KeyboardZombieController(mode, new CommandBridge(command -> {
            sent.add(command);
            return true;
        }));
    }

    @Test
    @DisplayName("the cursor opens inside the half this player is allowed to use")
    void opensOnTheZombieSide() {
        assertTrue(keyboard.cursor().col() >= mode.getRedLineColumn());
        assertTrue(keyboard.cursor().col() < Constants.BOARD_COLS);
        assertTrue(keyboard.cursor().row() >= 0);
        assertTrue(keyboard.cursor().row() < Constants.BOARD_ROWS);
        assertNotNull(keyboard.armed(), "something has to be selected before the first Space");
    }

    @Test
    @DisplayName("W is up the screen, which is DOWN the row index")
    void wGoesUpTheScreen() {
        int before = keyboard.cursor().row();
        keyboard.keyDown(Input.Keys.W);
        // Row 0 is the top lane in the model and the bottom of the screen. Getting this backwards is
        // invisible in a screenshot and unplayable in the hand.
        assertEquals(before - 1, keyboard.cursor().row());
        keyboard.keyDown(Input.Keys.S);
        assertEquals(before, keyboard.cursor().row());
    }

    @Test
    @DisplayName("the cursor cannot leave the board or cross the red line")
    void theCursorIsPennedIn() {
        for (int i = 0; i < 20; i++) {
            keyboard.keyDown(Input.Keys.A);
            keyboard.keyDown(Input.Keys.W);
        }
        assertEquals(mode.getRedLineColumn(), keyboard.cursor().col(),
                "a cursor that can wander somewhere it can never act spends its time in the wrong place");
        assertEquals(0, keyboard.cursor().row());

        for (int i = 0; i < 20; i++) {
            keyboard.keyDown(Input.Keys.D);
            keyboard.keyDown(Input.Keys.S);
        }
        assertEquals(Constants.BOARD_COLS - 1, keyboard.cursor().col());
        assertEquals(Constants.BOARD_ROWS - 1, keyboard.cursor().row());
    }

    @Test
    @DisplayName("the digits pick off the belt, in the order it is drawn")
    void digitsPickFromTheBelt() {
        List<String> roster = new ArrayList<>(mode.getRoster().keySet());
        keyboard.keyDown(Input.Keys.NUM_3);
        assertEquals(roster.get(2), keyboard.armed());
        keyboard.keyDown(Input.Keys.NUM_1);
        assertEquals(roster.get(0), keyboard.armed());
    }

    @Test
    @DisplayName("Space produces the same command a click and a typed line produce")
    void spaceProducesTheGameCommand() {
        keyboard.keyDown(Input.Keys.NUM_2);
        keyboard.keyDown(Input.Keys.SPACE);

        assertEquals(1, sent.size());
        String command = sent.get(0);
        // The whole reason couch play needed almost nothing: this is the identical string the mouse
        // player's click makes, the network build sends, and the terminal accepts at its prompt.
        assertTrue(InGameRegex.SUMMON_ZOMBIE.matches(command),
                "not a command the engine would dispatch: " + command);
        assertEquals(keyboard.armed(), InGameRegex.SUMMON_ZOMBIE.getGroup(command, "type"));
        assertEquals(String.valueOf(keyboard.cursor().col()),
                InGameRegex.SUMMON_ZOMBIE.getGroup(command, "x"));
        assertEquals(String.valueOf(keyboard.cursor().row()),
                InGameRegex.SUMMON_ZOMBIE.getGroup(command, "y"));
    }

    @Test
    @DisplayName("keys nobody bound are left for whoever is next in the multiplexer")
    void unboundKeysFallThrough() {
        // The plant player's stage is behind this one in the InputMultiplexer. Swallowing everything
        // would make the mouse player's Escape stop working.
        org.junit.jupiter.api.Assertions.assertFalse(keyboard.keyDown(Input.Keys.ESCAPE));
        org.junit.jupiter.api.Assertions.assertFalse(keyboard.keyDown(Input.Keys.NUM_9));
        assertTrue(sent.isEmpty());
    }
}
