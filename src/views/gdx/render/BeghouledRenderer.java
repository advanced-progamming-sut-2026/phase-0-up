package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import models.game.GameSession;
import models.game.gamemodes.BeghouledMode;
import views.gdx.core.Assets;
import views.gdx.map.LawnGeometry;

// The two things a Beghouled board needs that an ordinary lawn does not: holes where zombies ate, and
// some sign that a swap DID something.
//
// ## Why the board itself needed no work
//
// `BeghouledMode.onStart` plants a real, zombie-fighting `Plant` in every cell and `syncMap` keeps
// those in step with its logical board, so `PlantRenderer` already draws the whole 5x9 grid with no
// special-casing at all. That is why this class is small: the match-3 board was never a rendering
// problem, only a FEEDBACK one.
//
// ## The cascade is watched, not announced
//
// A swap resolves entirely inside one call: clear the runs, collapse the columns, refill, repeat until
// nothing matches. By the time any frame is drawn it is over, and the model reports only a total
// ("Match! 150 sun banked"). Asking it to narrate each run would put a cosmetic concern into a mode's
// hot path and change text the terminal build prints.
//
// What the view CAN see is the board before and after: every cell whose plant TYPE changed is, by
// definition, a cell the resolve touched -- cleared, dropped into, or refilled. Diffing `board()`
// frame to frame therefore recovers the whole cascade for free, and each changed cell gets a brief
// settle flash so the eye can follow what fell where. Same reasoning as `DamageFlash` and the armour
// fly-off: the model has no event for it and should not grow one.
public final class BeghouledRenderer {

    // The game's own crater art, 129x131 at the 768 resolution -- almost exactly a lawn cell once the
    // sprite scale is applied. Used as a flat region rather than through SpriteRegistry because the
    // dump's CRATER animation carries NO clips at all ("clips":{}), so there is nothing to play.
    private static final String CRATER_ART = "IMAGE_EFFECTS_CRATER_CRATER_129X131";

    // How long a cell flashes after its plant LANDS.
    //
    // Shorter than it was, and it now fires at the end of the fall rather than at the start of it. A
    // flash is the punctuation on a piece arriving, and half a second of wash over a piece that was
    // still in the air read as the cell being highlighted rather than as anything landing in it.
    private static final float SETTLE_TIME = 0.28f;

    // The flash is a white wash rather than a tint, so it reads on a Wall-nut and a Puff-shroom alike.
    private static final Color SETTLE = new Color(1f, 1f, 0.85f, 0.7f);

    // ## The drop
    //
    // The board's whole vocabulary was one white flash. A match-3 board reads as a match-3 board because
    // its pieces FALL -- the run vanishes, the column collapses into the gap, and new pieces come in off
    // the top -- and none of that was drawn: every changed cell simply held a different plant on the
    // next frame, flashed, and that was the entire cascade.
    //
    // The model cannot help. `BeghouledMode` resolves a swap in a single call -- clear, collapse, refill,
    // repeat until nothing matches -- so by the time any frame is drawn it is over and the intermediate
    // boards no longer exist. There is no replaying the real cascade. What CAN be recovered is where
    // each piece ended up, and that is enough: every changed cell in a column is a cell the collapse
    // moved something into, so the whole column's changed run is dropped in as one.
    //
    // One distance per column, not per cell, and that is what makes it read correctly: a collapsing
    // column moves as a body, so its pieces land together. Distances that varied down the column would
    // stretch it apart in mid-air and land the bottom last, which is the opposite of how a column falls.
    private static final float FALL_GRAVITY_CELLS = 60f;

    // A deep column would otherwise start its pieces well above the lawn, where there is no board for
    // them to fall through -- a plant briefly drawn over the sky above lane 0. Capped low enough to stay
    // roughly within the row above, which for art already a cell tall is barely off the board at all.
    private static final float MAX_FALL_CELLS = 1.2f;

    // A crater is a hole in the ground, so it is drawn slightly INSIDE its tile: filling the cell edge
    // to edge makes the lawn look tiled with holes rather than pocked by them.
    private static final float CRATER_INSET = 0.08f;

    private final Assets assets;
    private final LawnGeometry lawn;

    // Last frame's board, and how long ago each cell changed. Sized on first use, because the mode
    // decides the board's size in onStart and this is built before that has necessarily run.
    private String[][] seen;
    private float[][] settling;

    // How far each cell's piece still has to fall, in cells, and how long it has been falling. A
    // fallHeight of 0 means the cell is at rest, which is every cell on almost every frame.
    private float[][] fallHeight;
    private float[][] fallAge;

    private TextureRegion crater;
    private boolean craterResolved;

    public BeghouledRenderer(Assets assets, LawnGeometry lawn) {
        this.assets = assets;
        this.lawn = lawn;
    }

    // Null on every board that is not Beghouled, which is the single instanceof this whole class costs
    // on an ordinary level.
    public static BeghouledMode modeOf(GameSession session) {
        return session != null && session.getMode() instanceof BeghouledMode mode ? mode : null;
    }

    // Ages the settle flashes and picks up the board's changes. Once per frame from GameRenderer, never
    // from the lane pass -- that visits five times a frame, and ageing there would run every flash at
    // five times its own speed. The trap ZombieActions documents.
    public void advance(GameSession session, float delta) {
        BeghouledMode mode = modeOf(session);
        if (mode == null) {
            return;
        }
        String[][] board = mode.board();
        if (board == null) {
            return;
        }
        if (seen == null || seen.length != board.length) {
            seen = new String[board.length][board[0].length];
            settling = new float[board.length][board[0].length];
            fallHeight = new float[board.length][board[0].length];
            fallAge = new float[board.length][board[0].length];
        }
        advanceFalls(board, delta);
        int changed = markChanged(board);
        // A cascade is over in one call and the drop and its flash together last well under a second, so
        // "did it fire" cannot be read off a screenshot taken at the wrong moment. The count can.
        if (changed > 0 && views.gdx.core.DebugFlags.BOARD_COUNTS) {
            com.badlogic.gdx.Gdx.app.log("Beghouled", changed + " cells dropped (" + mode.getMatchesMade()
                    + "/" + mode.getMatchTarget() + " matches)");
        }
    }

    // Ages every fall and every flash, and fires the flash on the frame a piece lands.
    private void advanceFalls(String[][] board, float delta) {
        for (int r = 0; r < board.length; r++) {
            for (int c = 0; c < board[r].length; c++) {
                settling[r][c] = Math.max(0f, settling[r][c] - delta);
                if (fallHeight[r][c] <= 0f) {
                    continue;
                }
                fallAge[r][c] += delta;
                if (fallAge[r][c] >= landingTime(fallHeight[r][c])) {
                    fallHeight[r][c] = 0f;
                    fallAge[r][c] = 0f;
                    // The flash belongs to the landing, not to the change that started the fall.
                    settling[r][c] = SETTLE_TIME;
                }
            }
        }
    }

    // Picks up the board's changes and starts a fall for every column they touch. Returns how many
    // cells changed, for the debug count.
    //
    // Column by column rather than cell by cell, because the fall height is a property of the COLUMN:
    // it takes the whole column's changed count to know how far any one of its pieces dropped, so the
    // diff has to be complete before any of them can be launched.
    private int markChanged(String[][] board) {
        int changed = 0;
        boolean[] fell = new boolean[board.length];
        for (int c = 0; c < board[0].length; c++) {
            int inColumn = 0;
            for (int r = 0; r < board.length; r++) {
                String now = board[r][c];
                // The first frame fills `seen` from nothing, which would drop all 45 cells at once --
                // the opening board is dealt, not matched. A null previous value is that first frame.
                fell[r] = seen[r][c] != null && !seen[r][c].equals(now);
                if (fell[r]) {
                    inColumn++;
                }
                seen[r][c] = now;
            }
            if (inColumn == 0) {
                continue;
            }
            changed += inColumn;
            // One height for the whole column's changed run, so it lands as a body. See the note on
            // FALL_GRAVITY_CELLS.
            float height = Math.min(inColumn, MAX_FALL_CELLS);
            for (int r = 0; r < board.length; r++) {
                if (fell[r]) {
                    fallHeight[r][c] = height;
                    fallAge[r][c] = 0f;
                    // Cleared, not left running: a cell caught by a second cascade while it was still
                    // flashing from the first is falling again, and a flash under a piece in mid-air is
                    // the thing this whole change is replacing.
                    settling[r][c] = 0f;
                }
            }
        }
        return changed;
    }

    // When a piece dropped from `height` cells reaches the ground: h = gt^2/2, so t = sqrt(2h/g).
    private static float landingTime(float height) {
        return (float) Math.sqrt(2f * height / FALL_GRAVITY_CELLS);
    }

    // How far above its cell a piece currently is, in world pixels. Zero for a cell at rest, which is
    // every cell on almost every frame. This is what PlantRenderer draws the plant from.
    public float liftAt(int col, int row) {
        if (fallHeight == null || row < 0 || row >= fallHeight.length
                || col < 0 || col >= fallHeight[row].length || fallHeight[row][col] <= 0f) {
            return 0f;
        }
        float t = fallAge[row][col];
        // Remaining height, never negative: advanceFalls ends the fall on the frame it reaches zero,
        // but the frame it ends ON is still drawn.
        float remaining = fallHeight[row][col] - 0.5f * FALL_GRAVITY_CELLS * t * t;
        return Math.max(0f, remaining) * lawn.cellHeight();
    }

    // The craters, drawn with the terrain: a hole is ground, not something standing on it.
    public void drawCraters(Batch batch, GameSession session, int row) {
        BeghouledMode mode = modeOf(session);
        if (mode == null || mode.craters() == null || row >= mode.craters().length) {
            return;
        }
        TextureRegion art = craterArt();
        if (art == null) {
            return;
        }
        boolean[] craters = mode.craters()[row];
        float inset = lawn.cellWidth() * CRATER_INSET;
        for (int col = 0; col < craters.length; col++) {
            if (!craters[col]) {
                continue;
            }
            batch.draw(art,
                    SpritePlacer.toSpriteSpace(lawn.worldX(col) + inset),
                    SpritePlacer.toSpriteSpace(lawn.worldY(row) + inset),
                    SpritePlacer.toSpriteSpace(lawn.cellWidth() - inset * 2f),
                    SpritePlacer.toSpriteSpace(lawn.cellHeight() - inset * 2f));
        }
    }

    // The settle flash, drawn AFTER the plants so it lights the piece that just arrived rather than the
    // ground under it.
    public void drawSettles(Batch batch, GameSession session, int row) {
        if (settling == null || modeOf(session) == null || row >= settling.length) {
            return;
        }
        float previous = batch.getPackedColor();
        for (int col = 0; col < settling[row].length; col++) {
            float left = settling[row][col];
            if (left <= 0f) {
                continue;
            }
            // Fades out over its life, so a cascade reads as a ripple settling rather than as a row of
            // cells blinking off together.
            batch.setColor(Color.WHITE);
            assets.solid(new Color(SETTLE.r, SETTLE.g, SETTLE.b, SETTLE.a * (left / SETTLE_TIME)))
                    .draw(batch,
                            SpritePlacer.toSpriteSpace(lawn.worldX(col)),
                            SpritePlacer.toSpriteSpace(lawn.worldY(row) + liftAt(col, row)),
                            SpritePlacer.toSpriteSpace(lawn.cellWidth()),
                            SpritePlacer.toSpriteSpace(lawn.cellHeight()));
        }
        batch.setPackedColor(previous);
    }

    // Resolved once and cached, nulls included: assets.region throws for an id the atlas does not hold,
    // and a board full of craters would otherwise throw and catch once per crater per frame.
    private TextureRegion craterArt() {
        if (craterResolved) {
            return crater;
        }
        craterResolved = true;
        try {
            crater = assets.region(CRATER_ART);
        } catch (RuntimeException missing) {
            com.badlogic.gdx.Gdx.app.error("BeghouledRenderer",
                    CRATER_ART + " is not in the atlas -- craters will be invisible holes");
            crater = null;
        }
        return crater;
    }
}
