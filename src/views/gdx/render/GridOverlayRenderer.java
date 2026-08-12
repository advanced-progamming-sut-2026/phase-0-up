package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import utils.Constants;
import views.gdx.map.LawnGeometry;

// The red 5x9 grid the Settings menu can switch on ("Show Grid" in the phase-2 spec).
//
// It doubles as the calibration instrument for LawnGeometry: the lawn's origin and cell size are
// numbers eyeballed against painted background art, and the only practical way to check them is to
// draw the grid over the art and look. Run with -Dpvz.grid=1 and adjust -Dpvz.lawn until the lines sit
// on the furrows.
public final class GridOverlayRenderer implements Disposable {

    private static final Color GRID_COLOR = new Color(1f, 0.15f, 0.15f, 0.75f);
    // The lawn's outer boundary, drawn brighter so the origin and far corner are unambiguous while
    // calibrating.
    private static final Color BOUNDS_COLOR = new Color(1f, 0.85f, 0.1f, 0.9f);

    private final ShapeRenderer shapes = new ShapeRenderer();

    public void draw(Matrix4 projection, LawnGeometry lawn) {
        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Line);

        shapes.setColor(GRID_COLOR);
        for (int col = 0; col <= Constants.BOARD_COLS; col++) {
            float x = lawn.worldX(col);
            shapes.line(x, lawn.originY(), x, lawn.topEdge());
        }
        for (int row = 0; row <= Constants.BOARD_ROWS; row++) {
            // worldY takes a lane index, and there are ROWS+1 lines to draw, so the last one is the top
            // edge rather than a lane.
            float y = row == Constants.BOARD_ROWS ? lawn.topEdge() : lawn.worldY(row);
            shapes.line(lawn.originX(), y, lawn.rightEdge(), y);
        }

        shapes.setColor(BOUNDS_COLOR);
        shapes.rect(lawn.originX(), lawn.originY(),
                lawn.rightEdge() - lawn.originX(), lawn.topEdge() - lawn.originY());

        shapes.end();
    }

    // Fills one tile -- the white highlight under the cursor while planting (T2.1).
    //
    // Blending is turned on around the draw because ShapeRenderer does not do it for you: without this
    // the colour's alpha is ignored and the tile comes out as a solid block that hides the plant
    // standing on it, rather than a wash over the art.
    public void highlight(Matrix4 projection, LawnGeometry lawn, int col, int row, Color color) {
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(color);
        shapes.rect(lawn.worldX(col), lawn.worldY(row), lawn.cellWidth(), lawn.cellHeight());
        shapes.end();
        com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
