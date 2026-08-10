package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import models.entities.collectibles.Sun;
import models.entities.collectibles.SunType;
import models.game.GameSession;
import views.gdx.bridge.EntityInterpolator;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

// Draws suns.
//
// The SUN animation happens to carry its own colour variants as clips -- "animation" for the normal
// one, "red" and "blue" alternates -- so the three sun types the spec asks to look different are a
// clip choice rather than three sprites.
//
// Suns are drawn from Sun.getCurrentY() rather than Entity.getY(). getY() is the rounded lane, which
// would make a falling sun drop one whole row at a time instead of descending.
public final class CollectibleRenderer {

    private static final String SUN_SPRITE = "SUN";

    // How far above the lawn a sky sun starts, in cell heights. The model drops it from the top ROW,
    // which on screen is barely above the board and reads as the sun blinking into existence rather
    // than falling from the sky. Purely visual: the landing tile and the moment it lands stay the
    // model's, the descent is simply re-mapped onto a longer path.
    private static final float SKY_DROP_CELLS = 4.5f;

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final EntityInterpolator interpolator;
    private final AnimationClocks clocks;

    public CollectibleRenderer(SpriteRegistry sprites, LawnGeometry lawn,
                               EntityInterpolator interpolator, AnimationClocks clocks) {
        this.sprites = sprites;
        this.lawn = lawn;
        this.interpolator = interpolator;
        this.clocks = clocks;
    }

    public void draw(Batch batch, GameSession session, float delta, float alpha) {
        EntitySprite sprite = sprites.get(SUN_SPRITE);

        // GameSession.getActiveSuns(), NOT GameMap.getActiveCollectibles(). The map's list exists but
        // SunSystem never puts suns in it, so reading it renders nothing at all -- which is exactly
        // what happened until a per-second census showed suns=0 while the event log said they were
        // falling.
        for (Sun sun : new java.util.ArrayList<>(session.getActiveSuns())) {
            if (sun.isRemovable()) {
                continue;   // collected or expired; it is swept at the end of the tick
            }
            float modelY = (float) sun.getCurrentY();
            float x = lawn.worldX(interpolator.x(sun, sun.getX(), alpha));

            // INTERPOLATED height, not the raw model value. currentY only changes on a tick, so
            // reading it directly makes the sun descend in 10 visible steps a second -- the stutter.
            // The interpolator already samples it, so this is the same smoothing zombies get.
            float smoothY = interpolator.lane(sun, (int) modelY, alpha);

            float y;
            if (sun.isFalling()) {
                // The model drops a sun from the top ROW, which is barely above the board. Its progress
                // is re-mapped onto a much taller path so it reads as falling from the sky, while still
                // touching down on the same tile at the same moment.
                float target = Math.max(0.0001f, (float) sun.getTargetY());
                float progress = Math.max(0f, Math.min(1f, smoothY / target));
                float from = lawn.topEdge() + SKY_DROP_CELLS * lawn.cellHeight();
                float to = lawn.centerY((int) Math.round(sun.getTargetY()));
                y = from + (to - from) * progress;
            } else {
                y = laneToWorldY(smoothY);
            }

            String clip = clipFor(sprite, sun.getType());
            SpritePlacer.drawCentred(batch, sprite, clip,
                    ClipMap.sample(sprite, clip, clocks.advance(sun, clip, delta)), x, y, true);
        }
    }

    // A sun's y is a continuous lane coordinate while it falls, so it is converted the same way a
    // fractional zombie lane is -- interpolating between the lanes it is passing through.
    private float laneToWorldY(float lane) {
        int base = (int) Math.floor(lane);
        float frac = lane - base;
        int lower = Math.max(0, Math.min(base, utils.Constants.BOARD_ROWS - 1));
        int upper = Math.max(0, Math.min(base + 1, utils.Constants.BOARD_ROWS - 1));
        float low = lawn.centerY(lower);
        float high = lawn.centerY(upper);
        return low + (high - low) * frac;
    }

    private static String clipFor(EntitySprite sprite, SunType type) {
        if (type == null) {
            return ClipMap.firstAvailable(sprite, "animation");
        }
        return switch (type) {
            // Radioactive suns are the purple ones; "blue" is the closest alternate the animation has.
            case RADIOACTIVE -> ClipMap.firstAvailable(sprite, "blue", "animation");
            // The big special sun reuses the warm variant so it reads apart from a normal drop.
            case SPECIAL -> ClipMap.firstAvailable(sprite, "red", "animation");
            case NORMAL -> ClipMap.firstAvailable(sprite, "animation");
        };
    }
}
