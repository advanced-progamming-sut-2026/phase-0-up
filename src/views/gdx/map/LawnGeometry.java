package views.gdx.map;

import models.game.EnvironmentType;
import utils.Constants;

// The single translation between where the model thinks something is and where it gets drawn.
//
// Three mismatches have to be reconciled here, and nowhere else:
//
//  1. A zombie's x is a continuous double that DECREASES as it advances (spawns at 9.5, the house is
//     at 0), while a plant's column is an int. Both map through the same worldX().
//  2. The model's row 0 is the TOP lane and rows count downward; LibGDX's y axis points up. So lane
//     order has to be flipped, and every renderer getting that wrong independently is exactly the kind
//     of bug that is invisible until zombies eat the wrong plants.
//  3. World space here is BACKGROUND PIXEL space, not screen space. The Ancient Egypt background is
//     1975x768 (a 278-wide left slice, a 1024 middle, a 673 right), and the camera decides how much of
//     that is on screen. Keeping the lawn in background pixels means the grid stays welded to the art
//     at any window size.
public final class LawnGeometry {

    // Full background extent, in its own pixels. All four worlds ship 768-tall art.
    public static final float WORLD_HEIGHT = 768f;

    private final float originX;   // left edge of column 0
    private final float originY;   // BOTTOM edge of the bottom lane (row 4)
    private final float cellWidth;
    private final float cellHeight;

    public LawnGeometry(float originX, float originY, float cellWidth, float cellHeight) {
        this.originX = originX;
        this.originY = originY;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
    }

    // Per-world starting values. Calibrated against the background art with the grid overlay on; see
    // `-Dpvz.lawn=originX,originY,cellW,cellH` for re-tuning without a recompile.
    public static LawnGeometry forEnvironment(EnvironmentType environment) {
        LawnGeometry override = fromSystemProperty();
        if (override != null) {
            return override;
        }
        // Measured off the Ancient Egypt art rather than guessed: the painted tile boundaries were
        // located by running an edge-detection pass over a clean background render, which put the
        // column pitch at 82.1 world px and the row pitch at 97.2. Tiles really are taller than they
        // are wide -- that is the board's perspective, not a mistake.
        //
        // The four worlds share one lawn footprint (all four backgrounds are the same 278/1024/673
        // three-slice layout), so one set of numbers covers them. Split into a switch the moment one
        // of them actually disagrees.
        return new LawnGeometry(528f, 84f, 82f, 97f);
    }

    // Calibration hook: -Dpvz.lawn=345,95,128,118
    private static LawnGeometry fromSystemProperty() {
        String raw = System.getProperty("pvz.lawn");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("\\s*,\\s*");
        if (parts.length != 4) {
            System.err.println("Ignoring -Dpvz.lawn=" + raw + " (expected originX,originY,cellW,cellH)");
            return null;
        }
        try {
            return new LawnGeometry(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]),
                    Float.parseFloat(parts[2]), Float.parseFloat(parts[3]));
        } catch (NumberFormatException e) {
            System.err.println("Ignoring -Dpvz.lawn=" + raw + " (not four numbers)");
            return null;
        }
    }

    // Continuous model x (0 = house edge, 9 = far right, zombies spawn at 9.5) -> world x.
    public float worldX(double modelX) {
        return originX + (float) modelX * cellWidth;
    }

    // Horizontal centre of a column, where a plant sits.
    public float centerX(int col) {
        return originX + (col + 0.5f) * cellWidth;
    }

    // BOTTOM edge of a lane -- the line an entity's feet stand on. Row 0 is the top lane, so the row
    // index is inverted against LibGDX's upward y.
    public float worldY(int lane) {
        return originY + (Constants.BOARD_ROWS - 1 - lane) * cellHeight;
    }

    // Vertical centre of a lane, for things that float rather than stand (suns, projectiles).
    public float centerY(int lane) {
        return worldY(lane) + cellHeight * 0.5f;
    }

    // Inverse of the above: which tile does this world point fall in? Returns an out-of-range GridPos
    // rather than null when the point is off the lawn, so callers can use isValid() uniformly.
    public GridPos cellAt(float worldXPos, float worldYPos) {
        int col = (int) Math.floor((worldXPos - originX) / cellWidth);
        int fromBottom = (int) Math.floor((worldYPos - originY) / cellHeight);
        int row = Constants.BOARD_ROWS - 1 - fromBottom;
        return new GridPos(col, row);
    }

    public float cellWidth() {
        return cellWidth;
    }

    public float cellHeight() {
        return cellHeight;
    }

    public float originX() {
        return originX;
    }

    public float originY() {
        return originY;
    }

    // Right edge of the lawn -- where a zombie stops being on the board.
    public float rightEdge() {
        return originX + Constants.BOARD_COLS * cellWidth;
    }

    public float topEdge() {
        return originY + Constants.BOARD_ROWS * cellHeight;
    }

    // Printed by the calibration overlay so a tuned layout can be pasted straight back into the
    // defaults above.
    public String describe() {
        return String.format("-Dpvz.lawn=%.0f,%.0f,%.0f,%.0f", originX, originY, cellWidth, cellHeight);
    }

    public LawnGeometry nudged(float dx, float dy, float dw, float dh) {
        return new LawnGeometry(originX + dx, originY + dy,
                Math.max(1f, cellWidth + dw), Math.max(1f, cellHeight + dh));
    }
}
