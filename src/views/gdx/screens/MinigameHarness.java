package views.gdx.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import controllers.engine.GameEngine;
import models.game.GameSession;
import views.gdx.core.DebugFlags;
import views.gdx.input.LawnInputProcessor;
import views.gdx.input.ToolState;
import views.gdx.map.LawnGeometry;

// The unattended drivers for the three mini-games, which are the only boards a screenshot cannot reach
// on its own.
//
// Every state worth looking at on them -- a smashed vase, a packet on the ground, a nut mid-roll, an
// eaten brain -- exists only AFTER a gesture, and each gesture is a pair: arm something in the HUD, then
// click a lawn tile. -Dpvz.click reaches neither half (a vase is a Table, not a TextButton; a lawn tile
// is not an actor at all), so these drive ToolState and LawnInputProcessor directly. Going through the
// input processor rather than posting the command string is the point: it exercises the
// unproject-to-tile half of the path as well as the look.
//
// Lifted out of GameScreen when that class hit Checkstyle's 500-NCSS ceiling. It is a clean seam
// anyway: none of this runs in a normal game, and all of it is about driving the screen rather than
// drawing it.
final class MinigameHarness {

    private final GameSession session;
    private final GameEngine engine;
    private final LawnGeometry lawn;
    private final Viewport viewport;
    private final LawnInputProcessor lawnInput;
    private final ToolState tools;

    MinigameHarness(GameSession session, GameEngine engine, LawnGeometry lawn, Viewport viewport,
                    LawnInputProcessor lawnInput, ToolState tools) {
        this.session = session;
        this.engine = engine;
        this.lawn = lawn;
        this.viewport = viewport;
        this.lawnInput = lawnInput;
        this.tools = tools;
    }

    // Called once a frame from GameScreen.render. Each check is off unless its flag is set.
    void tick() {
        runVaseCheck();
        runBowlCheck();
        runSummonCheck();
        runSpawn();
        runCommands();
        runSwapCheck();
    }

    // -Dpvz.swapCheck=N. Makes N matches on a Beghouled board by trying neighbours, the way a player
    // does.
    //
    // Every state that board has worth looking at -- a cascade settling, a run of craters, enough sun
    // for an upgrade, the match counter past zero -- exists only AFTER a successful swap, and a swap is
    // two clicks on lawn tiles. -Dpvz.click reaches neither (a tile is not an actor) and -Dpvz.run
    // could post the command string directly, but that would skip the entire half of this task that is
    // actually new: unproject, pick the tile, decide it is a neighbour, build the string.
    //
    // Brute force on purpose. The mode can say whether a move exists (`hasAnyValidMove`) but not WHICH,
    // and adding a finder to the model for a harness would be the tail wagging the dog. A refused swap
    // is free -- the model puts the plants back and says so -- so walking the pairs until one lands is
    // exactly what a player does, and it exercises the refusal path as well.
    //
    //   gradlew runGui -Dpvz.screen=game -Dpvz.devMinigame=beghouled -Dpvz.skipIntro=1 \
    //       -Dpvz.swapCheck=3 -Dpvz.smokeFrames=120
    private static final int SWAP_START_FRAME = 30;
    // One pair per frame. Faster than a player, slow enough that each swap's resolve lands in its own
    // frame -- and the settle flash is 0.35s, so a whole burst on one frame would be one flash.
    private static final int SWAP_EVERY_FRAMES = 2;

    private int swapCheckFrames;
    // Where the sweep has got to, as a flat index over (row, col, direction) so it never retries a pair.
    private int swapCursor;
    private int swapsMade;
    // The tile the second tap is due to land on, held over to the next frame. See runSwapCheck.
    private int[] pendingSwap;

    private void runSwapCheck() {
        if (DebugFlags.SWAP_CHECK < 1) {
            return;
        }
        models.game.gamemodes.BeghouledMode mode = views.gdx.ui.UpgradePanel.modeOf(session);
        if (mode == null || ++swapCheckFrames < SWAP_START_FRAME
                || swapCheckFrames % SWAP_EVERY_FRAMES != 0) {
            return;
        }
        if (swapsMade >= DebugFlags.SWAP_CHECK) {
            return;
        }
        int before = mode.getMatchesMade();
        // The second tap lands a frame after the first, on purpose: with both on one frame the picked-up
        // tile is selected and released inside a single render, so its highlight never survives to be
        // drawn -- and a highlight nothing can screenshot is a highlight nobody can check.
        if (pendingSwap != null) {
            clickTile(pendingSwap[0], pendingSwap[1]);
            pendingSwap = null;
        } else if (!tryNextPair(mode)) {
            return;
        }
        if (mode.getMatchesMade() > before) {
            swapsMade++;
            Gdx.app.log("SwapCheck", "match " + swapsMade + " of " + DebugFlags.SWAP_CHECK
                    + " -- board is now " + mode.getMatchesMade() + "/" + mode.getMatchTarget()
                    + ", sun " + session.getSunAmount());
        }
    }

    // Clicks one pair of neighbours. False once the sweep has run out of pairs, which on a board with a
    // valid move somewhere should never happen before the target is reached.
    private boolean tryNextPair(models.game.gamemodes.BeghouledMode mode) {
        int rows = mode.getRows();
        int cols = mode.getCols();
        int pairs = rows * cols * 2;
        while (swapCursor < pairs) {
            int index = swapCursor++;
            int direction = index % 2;
            int col = (index / 2) % cols;
            int row = (index / 2) / cols;
            int toCol = direction == 0 ? col + 1 : col;
            int toRow = direction == 0 ? row : row + 1;
            if (toCol >= cols || toRow >= rows) {
                continue;
            }
            // The two presses are deliberately on DIFFERENT frames -- see pendingSwap. This is the
            // tap-tap half of the gesture; the drag half goes through the same beghouledDown and
            // isNeighbour, so one of them covers both.
            clickTile(col, row);
            pendingSwap = new int[] {toCol, toRow};
            return true;
        }
        Gdx.app.error("SwapCheck", "ran out of pairs after " + swapsMade + " matches");
        return false;
    }

    // -Dpvz.run=<cmd>;<cmd>. See DebugFlags: the general escape hatch for anything a command can say.
    //
    // Deliberately LATER than -Dpvz.spawn's frame and the fast-forward's, because the useful cases set
    // something up and then act on it -- a bomb dropped on an empty lawn is a no-op that looks exactly
    // like the flag not working.
    private static final int RUN_FRAME = 50;

    private int runFrames;

    // A command may be prefixed "@<frame>:" to post it that many frames in, instead of at RUN_FRAME.
    //
    // Everything else here fires the whole list on one frame, which cannot express a SECOND event --
    // feeding a plant that is already boosted, planting into a tile something has since happened to.
    // Those are one-frame states with a before and an after, and no other flag can reach the after.
    private void runCommands() {
        if (DebugFlags.RUN.isEmpty()) {
            return;
        }
        runFrames++;
        for (String command : DebugFlags.RUN.split(";")) {
            String trimmed = command.trim();
            int at = RUN_FRAME;
            if (trimmed.startsWith("@")) {
                int colon = trimmed.indexOf(':');
                if (colon > 1) {
                    try {
                        at = Integer.parseInt(trimmed.substring(1, colon).trim());
                        trimmed = trimmed.substring(colon + 1).trim();
                    } catch (NumberFormatException ignored) {
                        at = RUN_FRAME;   // not a frame prefix after all; run it as written
                    }
                }
            }
            if (trimmed.isEmpty() || runFrames != at) {
                continue;
            }
            // As with -Dpvz.spawn, the return value only says the string ROUTED. A command the engine
            // ran and then refused answers in a toast.
            boolean routed = engine.submitInGameCommand(trimmed);
            Gdx.app.log("Run", trimmed + (routed ? "" : "  -- matched no command pattern"));
        }
    }

    // -Dpvz.spawn=<alias,alias,...>. See DebugFlags: the only way to put ONE named zombie on real
    // ground and look at it.
    //
    // Delayed a few frames rather than run from the constructor, for the same reason the click checks
    // are: the board opens paused behind its objective card, and a zombie placed before the first tick
    // is placed into a session that has not started moving yet.
    private static final int SPAWN_FRAME = 20;
    private static final int DEFAULT_SPAWN_COLUMN = 5;

    private int spawnFrames;

    private void runSpawn() {
        if (DebugFlags.SPAWN.isEmpty() || spawnFrames > SPAWN_FRAME) {
            return;
        }
        if (++spawnFrames != SPAWN_FRAME) {
            return;
        }
        int column = DebugFlags.SPAWN_COLUMN >= 0
                ? DebugFlags.SPAWN_COLUMN : DEFAULT_SPAWN_COLUMN;
        String[] aliases = DebugFlags.SPAWN.split(",");
        for (int i = 0; i < aliases.length && i < utils.Constants.BOARD_ROWS; i++) {
            String alias = aliases[i].trim();
            if (alias.isEmpty()) {
                continue;
            }
            // The game's own cheat command, so an unknown alias is refused with a sentence instead of
            // quietly putting nothing on the lawn. Its return value only says the string ROUTED, not
            // that a zombie appeared -- the refusal ("no zombie called X has ever shambled by") arrives
            // as a toast, which is where to look when a lane comes up empty.
            boolean routed = engine.submitInGameCommand(
                    "cheat spawn-zombie -t " + alias + " -l (" + column + ", " + i + ")");
            Gdx.app.log("Spawn", alias + " in lane " + i + " at column " + column
                    + (routed ? "" : " -- the command did not even parse"));
        }
    }

    // -Dpvz.vaseCheck=N. Two passes, because a packet cannot be picked up on the frame the vase holding
    // it broke: the smash is a command, and the model does not process it until the next tick.
    private static final int VASE_SMASH_FRAME = 40;
    private static final int VASE_COLLECT_FRAME = 70;

    private int vaseCheckFrames;

    private void runVaseCheck() {
        if (DebugFlags.VASE_CHECK < 1) {
            return;
        }
        vaseCheckFrames++;
        if (vaseCheckFrames == VASE_SMASH_FRAME) {
            smashVases();
        } else if (vaseCheckFrames == VASE_COLLECT_FRAME) {
            collectDroppedSeeds();
        }
    }

    private void smashVases() {
        models.game.gamemodes.VaseBreakerMode mode =
                views.gdx.render.VaseRenderer.modeOf(session);
        if (mode == null) {
            Gdx.app.error("VaseCheck", "not a Vasebreaker board");
            return;
        }
        int smashed = 0;
        for (models.entities.interactables.Vase vase : mode.getVases()) {
            if (smashed >= DebugFlags.VASE_CHECK) {
                break;
            }
            if (!vase.isBroken()) {
                clickTile(vase.getX(), vase.getY());
                smashed++;
            }
        }
        Gdx.app.log("VaseCheck", "clicked " + smashed + " vases of " + mode.vaseCount());
    }

    private void collectDroppedSeeds() {
        models.game.gamemodes.VaseBreakerMode mode =
                views.gdx.render.VaseRenderer.modeOf(session);
        if (mode == null) {
            return;
        }
        int taken = 0;
        for (int row = 0; row < utils.Constants.BOARD_ROWS; row++) {
            for (int col = 0; col < utils.Constants.BOARD_COLS; col++) {
                if (mode.hasDroppedSeed(col, row)) {
                    clickTile(col, row);
                    taken++;
                }
            }
        }
        Gdx.app.log("VaseCheck", "picked up " + taken + " packets; hand is "
                + mode.plantInventory());
    }

    // -Dpvz.bowlCheck=N. One nut per lane, from column 0, taken off the belt in belt order.
    private static final int BOWL_FRAME = 45;

    private int bowlCheckFrames;

    private void runBowlCheck() {
        if (DebugFlags.BOWL_CHECK < 1) {
            return;
        }
        bowlCheckFrames++;
        if (bowlCheckFrames != BOWL_FRAME) {
            return;
        }
        models.game.gamemodes.WallnutBowlingMode mode =
                views.gdx.render.BowlingRenderer.modeOf(session);
        if (mode == null) {
            Gdx.app.error("BowlCheck", "not a Wall-nut Bowling board");
            return;
        }
        // The belt is read ONCE up front: every roll removes a nut from it, so re-reading it mid-loop
        // would skip whichever nut slid into the gap.
        java.util.List<models.entities.plants.bowling.BowlingKind> belt = mode.getConveyor();
        int rolled = 0;
        for (models.entities.plants.bowling.BowlingKind kind : belt) {
            if (rolled >= DebugFlags.BOWL_CHECK) {
                break;
            }
            tools.selectNut(views.gdx.render.BowlingRenderer.tokenFor(kind));
            clickTile(0, rolled % utils.Constants.BOARD_ROWS);
            rolled++;
        }
        Gdx.app.log("BowlCheck", "rolled " + rolled + " nuts; " + mode.getBalls().size()
                + " on the lawn, belt now " + mode.getConveyor());
    }

    // -Dpvz.summonCheck=N. One zombie per lane, onto the far right column.
    private static final int SUMMON_FRAME = 45;
    // Then a fast-forward, because a summoned zombie needs minutes of real play to walk the nine
    // columns to a brain -- and an EATEN brain is a state no screenshot can otherwise reach. Uses the
    // game's own `advance time` command, so the ticks it skips are ticks that really ran.
    private static final int SUMMON_ADVANCE_FRAME = 75;
    private static final int SUMMON_ADVANCE_TICKS = 1200;

    private int summonCheckFrames;

    private void runSummonCheck() {
        if (DebugFlags.SUMMON_CHECK < 1) {
            return;
        }
        summonCheckFrames++;
        if (summonCheckFrames == SUMMON_ADVANCE_FRAME) {
            models.game.gamemodes.IZombieMode mode = views.gdx.render.IZombieRenderer.modeOf(session);
            if (mode == null) {
                return;   // nothing to fast-forward TO; leave whatever board this is alone
            }
            engine.submitInGameCommand("advance time -t " + SUMMON_ADVANCE_TICKS + " ticks");
            Gdx.app.log("SummonCheck", "after " + SUMMON_ADVANCE_TICKS + " ticks: brains eaten "
                    + mode.brainsEaten() + " of " + mode.brainsTotal());
            return;
        }
        if (summonCheckFrames != SUMMON_FRAME) {
            return;
        }
        models.game.gamemodes.IZombieMode mode = views.gdx.render.IZombieRenderer.modeOf(session);
        if (mode == null) {
            Gdx.app.error("SummonCheck", "not an I, Zombie board");
            return;
        }
        // Cheapest first: the opening bank is 300 sun, and buying in roster order spends it on one
        // Gargantuar and shows nothing.
        java.util.List<java.util.Map.Entry<String, Integer>> roster =
                new java.util.ArrayList<>(mode.getRoster().entrySet());
        roster.sort(java.util.Map.Entry.comparingByValue());

        int summoned = 0;
        for (java.util.Map.Entry<String, Integer> entry : roster) {
            if (summoned >= DebugFlags.SUMMON_CHECK) {
                break;
            }
            tools.selectZombie(entry.getKey());
            clickTile(utils.Constants.BOARD_COLS - 2, summoned % utils.Constants.BOARD_ROWS);
            summoned++;
        }
        Gdx.app.log("SummonCheck", "summoned " + summoned + " zombies; sun left "
                + session.getSunAmount() + "; brains eaten " + mode.brainsEaten()
                + " of " + mode.brainsTotal());
    }

    // A real click on a tile, through the same processor the mouse drives.
    private void clickTile(int col, int row) {
        Vector3 point =
                new Vector3(lawn.centerX(col), lawn.centerY(row), 0f);
        viewport.project(point);
        int screenX = (int) point.x;
        int screenY = toMouseY(point.y);
        lawnInput.touchDown(screenX, screenY, 0, Input.Buttons.LEFT);
    }

    // Projected y is OpenGL's (up from the bottom); pointer events want it down from the top.
    private static int toMouseY(float projectedY) {
        return (int) (Gdx.graphics.getHeight() - projectedY);
    }
}
