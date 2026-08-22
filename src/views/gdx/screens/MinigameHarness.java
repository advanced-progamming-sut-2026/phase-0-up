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
