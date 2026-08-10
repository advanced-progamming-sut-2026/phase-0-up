package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import models.entities.collectibles.Collectible;
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

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final EntityInterpolator interpolator;

    public CollectibleRenderer(SpriteRegistry sprites, LawnGeometry lawn,
                               EntityInterpolator interpolator) {
        this.sprites = sprites;
        this.lawn = lawn;
        this.interpolator = interpolator;
    }

    public void draw(Batch batch, GameSession session, float stateTime, float alpha) {
        EntitySprite sprite = sprites.get(SUN_SPRITE);

        for (Collectible collectible : session.getMap().getActiveCollectibles()) {
            if (collectible.isRemovable()) {
                continue;   // collected or expired; it is swept at the end of the tick
            }
            if (!(collectible instanceof Sun sun)) {
                continue;
            }
            float modelY = (float) sun.getCurrentY();
            float x = lawn.worldX(interpolator.x(sun, sun.getX(), alpha));
            float y = laneToWorldY(interpolator.lane(sun, (int) modelY, alpha));

            SpritePlacer.drawCentred(batch, sprite, clipFor(sprite, sun.getType()), stateTime,
                    x, y, true);
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
