package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import views.gdx.sprite.EntitySprite;

import java.util.Map;

// Stands a sprite on the ground instead of at an arbitrary origin.
//
// A .PAM's draw origin is wherever the artist put it -- roughly mid-body for a Peashooter, far above
// the feet for a Gargantuar, and different again for an Imp. Drawing everything at the lane's foot
// line therefore leaves characters floating or sunk by wildly different amounts, and fudging a
// per-species offset table would be a maintenance sink.
//
// libPVZ can report a clip's actual drawn extent, so the offset is computed instead of guessed:
// shifting by -bounds.y puts the BOTTOM of the artwork exactly on the requested line, for any entity.
public final class SpritePlacer {

    // Characters stand a little way into their tile rather than on its very back edge, which is what
    // makes a row read as ground rather than as a shelf. A fraction of cell height, so it scales with
    // the lawn.
    public static final float FOOT_INSET = 0.18f;

    // The .PAM artwork is authored larger than the background it stands on. A Peashooter's idle clip
    // measures 148.8 px tall while a lawn cell is 97, and a basic zombie is 250 -- two and a half
    // tiles, which is plainly wrong.
    //
    // The atlas gives the factor away: images named for their nominal size (peashooter_101x76) are
    // stored as 65x49 regions -- 0.643. libPVZ draws them back at nominal size, so everything arrives
    // 1/0.643 too big for a 768-tall background. Applying it here puts a Peashooter at 96.7 px, almost
    // exactly one tile, and a zombie at 1.7 tiles.
    public static final float SPRITE_SCALE = 0.643f;

    private SpritePlacer() { }

    // Scaling is applied to the batch's transform for the whole entity pass rather than per sprite:
    // setTransformMatrix flushes the batch, so doing it per entity would mean a draw call each. The
    // cost is that coordinates handed to a sprite must be pre-divided -- see toSpriteSpace.
    public static com.badlogic.gdx.math.Matrix4 beginScaled(Batch batch) {
        com.badlogic.gdx.math.Matrix4 previous = batch.getTransformMatrix().cpy();
        batch.setTransformMatrix(new com.badlogic.gdx.math.Matrix4(previous)
                .scale(SPRITE_SCALE, SPRITE_SCALE, 1f));
        return previous;
    }

    public static void endScaled(Batch batch, com.badlogic.gdx.math.Matrix4 previous) {
        batch.setTransformMatrix(previous);
    }

    // World coordinate -> the scaled space sprites are drawn in, so an entity still lands on the world
    // position asked for.
    public static float toSpriteSpace(float worldValue) {
        return worldValue / SPRITE_SCALE;
    }

    // Draws so the sprite's feet land on footY, centred horizontally on centreX.
    public static void drawStanding(Batch batch, EntitySprite sprite, String clip, float stateTime,
                                    float centreX, float footY, boolean faceRight,
                                    Map<String, Boolean> parts) {
        float x = toSpriteSpace(centreX);
        float y = toSpriteSpace(footY) - bottomOffset(sprite, clip);
        sprite.draw(batch, clip, stateTime, x, y, faceRight, parts);
    }

    // Draws centred on a point, for things that float rather than stand (suns, projectiles).
    public static void drawCentred(Batch batch, EntitySprite sprite, String clip, float stateTime,
                                   float centreX, float centreY, boolean faceRight) {
        Rectangle bounds = sprite.bounds(clip);
        float x = toSpriteSpace(centreX);
        float y = toSpriteSpace(centreY);
        if (bounds != null) {
            // Same y-down flip as bottomOffset: the artwork's centre sits at -(y + height/2).
            y += bounds.y + bounds.height / 2f;
        }
        sprite.draw(batch, clip, stateTime, x, y, faceRight, null);
    }

    // How far below the draw origin the artwork reaches, in GDX (y-up) space.
    //
    // libPVZ reports bounds in the .PAM's own coordinate system, which is y-DOWN like Flash: the
    // rectangle runs from bounds.y downward by bounds.height. Read as if it were y-up -- which is the
    // obvious mistake, and the one made here first -- every entity floats about half its own height
    // above the ground (measured: 33 world px for a Wall-nut).
    //
    // Flipping gives the true span as [-(y + height), -y], so the bottom edge is -(y + height).
    private static float bottomOffset(EntitySprite sprite, String clip) {
        // anchorBounds(), not bounds(clip): see EntitySprite.anchorBounds for why using the playing
        // clip's box made walking zombies float a lane above the ground.
        Rectangle bounds = sprite.anchorBounds();
        return bounds == null ? 0f : -(bounds.y + bounds.height);
    }
}
