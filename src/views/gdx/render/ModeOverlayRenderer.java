package views.gdx.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import models.game.GameSession;
import models.game.gamemodes.DeadLineMode;
import models.game.gamemodes.GameMode;
import models.game.gamemodes.IZombieMode;
import models.game.gamemodes.WallnutBowlingMode;
import utils.Constants;
import views.gdx.map.LawnGeometry;


// The lines and marks a SPECIAL level draws on the lawn that a standard one does not.
//
// Every rule these stand for is invisible: Dead Line's trip-wire kills instantly and is not painted on
// the background, and the bowling and I-Zombie boundaries are just numbers on a mode. The only way to
// learn any of them was to lose to them, which the modes' own opening banners half-fix in words. This
// is the other half.
//
// Save Our Seeds' defended tiles used to be here too, as a gold rim. They are now the game's own
// GOLDTILE animation, drawn by TerrainRenderer -- shipped art beats an invented one, and a tile the
// plant STANDS ON reads better than a box drawn around it.
//
// This is the ONE place in the view that switches on the mode's concrete type, and it does so to pick
// ART, never to decide a rule -- the boundary column, which plants are protected and whether a tile may
// be dug are all read back off the mode. A default hook on GameMode would be the wrong shape here: a
// "draw yourself" method on a model interface is exactly the models-depend-on-views edge the
// architecture test forbids.
//
// Shapes rather than sprites, deliberately. These are overlays on the board in the same sense as the
// grid and the placement highlight -- GridOverlayRenderer already draws those the same way -- and the
// dump ships no art for a boundary line that would not have to be stretched into something it is not.
public final class ModeOverlayRenderer implements Disposable {

    // Red for "cross this and the level is over", which is what all three boundaries mean.
    private static final Color BOUNDARY = new Color(0.95f, 0.16f, 0.12f, 1f);
    private static final Color BOUNDARY_GLOW = new Color(1f, 0.35f, 0.25f, 1f);
    private static final float BOUNDARY_WIDTH = 5f;
    private static final float GLOW_WIDTH = 22f;

    // One slow breath a second and a half, the same rate the ripe-pot tick bobs at. Fast enough to
    // catch the eye, slow enough not to strobe behind a fight.
    private static final float PULSE_HZ = 0.66f;

    private final ShapeRenderer shapes = new ShapeRenderer();

    private float clock;

    // delta is the ANIMATION delta, so the pulse freezes with the board when the game is paused.
    public void draw(Matrix4 projection, GameSession session, LawnGeometry lawn, float delta) {
        GameMode mode = session == null ? null : session.getMode();
        if (mode == null) {
            return;
        }
        clock += delta;

        if (mode instanceof DeadLineMode deadLine) {
            boundary(projection, lawn, deadLine.getDeadLineColumn());
        } else if (mode instanceof WallnutBowlingMode bowling) {
            // Not the same rule -- this one is the line you may not plant past -- but it is the same
            // "do not cross" and reads better as the same mark than as a second vocabulary.
            boundary(projection, lawn, bowling.getRedLineColumn());
        } else if (mode instanceof IZombieMode izombie) {
            boundary(projection, lawn, izombie.getRedLineColumn());
        }

    }

    // A vertical line at the LEFT edge of a column.
    //
    // worldX(col), not centerX(col): a zombie's x is continuous and the modes compare it against the
    // column index itself, so the fatal point is where column `col` begins -- half a cell from where a
    // plant in that column is drawn. Drawing it through the middle would put the mark half a tile from
    // the rule it stands for, which is worse than not drawing it.
    private void boundary(Matrix4 projection, LawnGeometry lawn, int column) {
        if (column < 0 || column > Constants.BOARD_COLS) {
            return;
        }
        float x = lawn.worldX(column);
        float bottom = lawn.originY();
        float height = lawn.topEdge() - bottom;
        float pulse = pulse();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // A soft haze either side, so the line is findable at a glance on busy artwork without the line
        // itself having to be thick enough to hide a zombie standing on it.
        shapes.setColor(BOUNDARY_GLOW.r, BOUNDARY_GLOW.g, BOUNDARY_GLOW.b, 0.10f + 0.10f * pulse);
        shapes.rect(x - GLOW_WIDTH * 0.5f, bottom, GLOW_WIDTH, height);

        shapes.setColor(BOUNDARY.r, BOUNDARY.g, BOUNDARY.b, 0.55f + 0.40f * pulse);
        shapes.rect(x - BOUNDARY_WIDTH * 0.5f, bottom, BOUNDARY_WIDTH, height);

        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }


    // 0..1 and back, once every PULSE_HZ seconds.
    private float pulse() {
        return (float) (0.5d + 0.5d * Math.sin(clock * PULSE_HZ * Math.PI * 2d));
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
