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

    // How long a cell flashes after its plant changes.
    private static final float SETTLE_TIME = 0.5f;

    // The flash is a white wash rather than a tint, so it reads on a Wall-nut and a Puff-shroom alike.
    private static final Color SETTLE = new Color(1f, 1f, 0.85f, 0.7f);

    // A crater is a hole in the ground, so it is drawn slightly INSIDE its tile: filling the cell edge
    // to edge makes the lawn look tiled with holes rather than pocked by them.
    private static final float CRATER_INSET = 0.08f;

    private final Assets assets;
    private final LawnGeometry lawn;

    // Last frame's board, and how long ago each cell changed. Sized on first use, because the mode
    // decides the board's size in onStart and this is built before that has necessarily run.
    private String[][] seen;
    private float[][] settling;

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
        }
        int changed = 0;
        for (int r = 0; r < board.length; r++) {
            for (int c = 0; c < board[r].length; c++) {
                settling[r][c] = Math.max(0f, settling[r][c] - delta);
                String now = board[r][c];
                // The first frame fills `seen` from nothing, which would flash all 45 cells at once --
                // the opening board is dealt, not matched. A null previous value is that first frame.
                if (seen[r][c] != null && !seen[r][c].equals(now)) {
                    settling[r][c] = SETTLE_TIME;
                    changed++;
                }
                seen[r][c] = now;
            }
        }
        // A cascade is over in one call and the flash is half a second, so "did it fire" cannot be read
        // off a screenshot taken at the wrong moment. The count can.
        if (changed > 0 && views.gdx.core.DebugFlags.BOARD_COUNTS) {
            com.badlogic.gdx.Gdx.app.log("Beghouled", changed + " cells settled (" + mode.getMatchesMade()
                    + "/" + mode.getMatchTarget() + " matches)");
        }
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
                            SpritePlacer.toSpriteSpace(lawn.worldY(row)),
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
