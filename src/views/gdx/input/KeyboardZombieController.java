package views.gdx.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import models.game.gamemodes.BrainLawn;
import utils.Constants;
import views.gdx.bridge.CommandBridge;
import views.gdx.map.GridPos;

import java.util.ArrayList;
import java.util.List;

// The second player's hands, on one keyboard.
//
// Couch play puts two people on one device, so exactly one of them can have the mouse. The plant
// player keeps it -- placing a plant is a spatial act and a cursor is the right tool for it -- and the
// zombie player gets a keyboard cursor instead: WASD to move it, 1-5 to pick a zombie off the belt,
// Space to summon.
//
// ## It goes through CommandBridge like everything else
//
// The summon is not applied to the model here; it becomes the same "summon -t X -l (c, r)" string a
// click produces and a terminal player types. That is what keeps the two players' input consistent
// with each other and with the network build: one command grammar, one set of rules, three ways of
// reaching it.
//
// ## Confined to the zombie half
//
// The cursor cannot cross the red line. The MODEL refuses a summon left of it anyway -- and would say
// so -- but a cursor that can wander somewhere it can never act is a cursor that spends most of its
// time in the wrong place, and the refusal toast would fire on every stray Space.
public final class KeyboardZombieController extends InputAdapter {

    private final BrainLawn lawn;
    private final CommandBridge commands;

    // The belt, in the order the roster is drawn, so key 1 is the leftmost card.
    private final List<String> roster;

    private int column;
    private int row;
    private int slot;

    public KeyboardZombieController(BrainLawn lawn, CommandBridge commands) {
        this.lawn = lawn;
        this.commands = commands;
        this.roster = new ArrayList<>(lawn.getRoster().keySet());
        // Starts at the far right, middle lane: where a zombie player looks first, and inside the
        // legal half by construction.
        this.column = Constants.BOARD_COLS - 1;
        this.row = Constants.BOARD_ROWS / 2;
    }

    @Override
    public boolean keyDown(int keycode) {
        return switch (keycode) {
            case Input.Keys.W, Input.Keys.UP -> move(0, 1);
            case Input.Keys.S, Input.Keys.DOWN -> move(0, -1);
            case Input.Keys.A, Input.Keys.LEFT -> move(-1, 0);
            case Input.Keys.D, Input.Keys.RIGHT -> move(1, 0);
            case Input.Keys.SPACE, Input.Keys.ENTER -> summon();
            case Input.Keys.NUM_1 -> select(0);
            case Input.Keys.NUM_2 -> select(1);
            case Input.Keys.NUM_3 -> select(2);
            case Input.Keys.NUM_4 -> select(3);
            case Input.Keys.NUM_5 -> select(4);
            default -> false;
        };
    }

    // Row 0 is the TOP lane in the model and the BOTTOM of the screen, so W (up on screen) is -1 in
    // model terms. Getting this backwards is invisible in a still frame and infuriating to play.
    private boolean move(int columns, int screenUp) {
        column = clamp(column + columns, lawn.getRedLineColumn(), Constants.BOARD_COLS - 1);
        row = clamp(row - screenUp, 0, Constants.BOARD_ROWS - 1);
        return true;
    }

    private boolean select(int index) {
        if (index < 0 || index >= roster.size()) {
            return false;
        }
        slot = index;
        return true;
    }

    private boolean summon() {
        if (roster.isEmpty()) {
            return false;
        }
        // The refusal -- wrong side of the line, not enough sun, no such lane -- comes back from the
        // model as a toast, exactly as it does for the mouse player.
        commands.summon(roster.get(slot), new GridPos(column, row));
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // Where the cursor is, for the renderer that draws it.
    public GridPos cursor() {
        return new GridPos(column, row);
    }

    // Which zombie is armed, so the HUD can light up the matching card.
    public String armed() {
        return roster.isEmpty() ? null : roster.get(slot);
    }
}
