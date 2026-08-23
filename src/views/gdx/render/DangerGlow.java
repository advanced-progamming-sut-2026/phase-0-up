package views.gdx.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import models.entities.zombies.Zombie;
import models.game.GameSession;
import models.map.Row;
import views.gdx.map.LawnGeometry;

// A red wash up the left edge of the lawn when something is about to get into the house.
//
// The board tells the player everything except the one thing that ends the level. A zombie two columns
// from the house looks exactly like a zombie six columns from the house -- same sprite, same walk, and
// the only difference is a position the player has to notice for themselves while watching the other
// four lanes. The lawnmower is the last line of defence and it is silent until it fires, by which point
// the lane is already spent.
//
// So this is a warning, and it is deliberately the loudest thing the view says that is not an
// explosion: it grows, it pulses, and it is red.
//
// ## Why it is drawn rather than shipped
//
// No art. That is the same call TerrainRenderer makes for necromancy tiles and low sand banks: this is
// LIGHT on the ground rather than an object standing on it, which is the same category as the grid and
// the placement highlight, both of which are drawn. A gradient also does something no shipped sprite
// could -- it is sized and coloured from a live distance every frame.
public final class DangerGlow implements Disposable {

    // How far from the house the warning starts, in columns. Three is about eleven seconds of walking
    // for a basic zombie: long enough to plant something, short enough that it is not on for the whole
    // level. Anything wider and the glow becomes the board's normal state and stops being a warning.
    //
    // Was 2.0, which measured as nothing at all: a zombie standing in column 1 sits at x = 1.5, which
    // is three quarters of the way through a two-column ramp, so the wash came out at about 8% alpha --
    // present in the arithmetic and invisible on the screen.
    private static final float WARN_COLUMNS = 3.0f;

    // How far across the lawn the wash reaches, in columns. Wider than the warning zone on purpose: the
    // glow is a property of the HOUSE being threatened, not a highlight on the zombie, so it is
    // anchored to the left edge and fades out to the right.
    private static final float REACH_COLUMNS = 3.2f;

    // How much of that reach is there from the first moment, before urgency widens it. A wash that
    // starts as a hairline and grows reads as a rendering artefact until it is already too late.
    private static final float REACH_FLOOR = 0.55f;

    private static final float MAX_ALPHA = 0.55f;

    // Twice a second, and never all the way off. A pulse that touches zero reads as a flicker; one that
    // stays between two thirds and full reads as a heartbeat.
    private static final float PULSE_HZ = 2.0f;
    private static final float PULSE_FLOOR = 0.66f;

    private static final Color EDGE = new Color(1f, 0.12f, 0.06f, 1f);

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final Color near = new Color();
    private final Color far = new Color();
    private float clock;

    // Advanced with the frame's real delta rather than the animation delta: this is a warning to the
    // PLAYER, and one frozen mid-pulse behind the pause panel would be a level that looks lost.
    public void draw(Matrix4 projection, GameSession session, LawnGeometry lawn, float delta) {
        clock += delta;
        float urgency = urgencyOf(session);
        if (urgency <= 0f) {
            return;
        }

        float pulse = PULSE_FLOOR + (1f - PULSE_FLOOR)
                * (0.5f + 0.5f * (float) Math.sin(clock * PULSE_HZ * (float) Math.PI * 2f));
        float alpha = MAX_ALPHA * urgency * pulse;

        near.set(EDGE).mul(1f, 1f, 1f, alpha);
        far.set(EDGE).mul(1f, 1f, 1f, 0f);

        float width = REACH_COLUMNS * lawn.cellWidth() * (REACH_FLOOR + (1f - REACH_FLOOR) * urgency);
        float height = lawn.topEdge() - lawn.originY();

        // ShapeRenderer does not enable blending for you: without this the alpha is ignored and the
        // wash comes out as a solid red slab over the lawn. The same trap GridOverlayRenderer.highlight
        // documents.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // Per-corner colours, which is what makes it a gradient rather than a red rectangle with a hard
        // edge down the middle of the lawn. Corners run bottom-left, bottom-right, top-right, top-left.
        shapes.rect(lawn.originX(), lawn.originY(), width, height, near, far, far, near);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // 0 when nothing is close, rising to 1 as the nearest zombie reaches the house.
    //
    // Read straight off the board rather than from an event, because "how close is the closest one" is
    // a continuous question and there is no sentence that answers it. Hypnotised zombies are skipped:
    // one walking back toward the house is fighting FOR the player, and warning about it would be the
    // view calling an ally a threat.
    private static float urgencyOf(GameSession session) {
        if (session == null || session.getMap() == null) {
            return 0f;
        }
        double nearest = Double.MAX_VALUE;
        for (Row row : session.getMap().getRows()) {
            for (Zombie zombie : row.getZombies()) {
                if (zombie.getState().isHypnotized() || zombie.getHealth().isDead()) {
                    continue;
                }
                nearest = Math.min(nearest, zombie.getMovement().getPositionX());
            }
        }
        if (nearest >= WARN_COLUMNS) {
            return 0f;
        }
        // Clamped at the low end: a zombie past the house sits at a negative x while the mower runs it
        // down, and without this the wash would keep growing after the danger had been dealt with.
        return (float) Math.min(1f, (WARN_COLUMNS - Math.max(0.0, nearest)) / WARN_COLUMNS);
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
