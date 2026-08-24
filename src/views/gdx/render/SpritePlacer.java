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
    // The factor is exactly 1/1.5625 = 0.64, which libPVZ's author confirms is the ratio between the
    // animations and the backgrounds: either the characters go up by 1.5625 or the background comes down
    // by 0.64. We shrink the characters, because the background is what the lane geometry is measured
    // against.
    //
    // Was 0.643 here, estimated from a single sample -- IMAGE_PLANT_PEASHOOTER's nominal 101x76 is packed
    // as a 65x49 region, and 65/101 is 0.6436. That is rounding noise: packed sizes are whole pixels, so
    // every region is out by up to half a pixel in each axis. Averaged over the 16,064 atlas regions whose
    // ids carry their nominal size, the ratio is 0.6407 wide and 0.6409 tall, and 0.64 lands within a
    // pixel of the packed size in both axes for every one of them.
    //
    // The correction is under half a percent -- about one pixel on a zombie -- so this changes nothing
    // visible. It is here so the number is the real one rather than an artefact of how it was measured.
    public static final float SPRITE_SCALE = 0.64f;

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

    // Switches the batch to additive blending, for a hit flash.
    //
    // A tint cannot brighten anything: setColor MULTIPLIES, so the whitest a sprite can be tinted is
    // its own colour. Drawing it a second time additively ADDS light on top, which is what actually
    // makes a plant or a zombie flash white. The alternative is a shader, which would be one more
    // thing to keep alive for a two-frame effect.
    public static void beginAdditive(Batch batch) {
        batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                com.badlogic.gdx.graphics.GL20.GL_ONE);
    }

    // Back to normal alpha blending. Leaving the batch additive would make everything drawn afterwards
    // -- the rest of the lane, the suns, the HUD -- glow.
    public static void endAdditive(Batch batch) {
        batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
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

    // The same, at a fraction of the sprite's authored size, still standing on footY.
    //
    // Needed for entities the model spawns as one thing and the view draws as another: I, Zombie's sun
    // makers are ordinary bucketheads in the model but are drawn as the disco mech, which is authored
    // far larger -- five of them stacked in the back column overlapped two lanes each. The scale is
    // applied to the batch transform rather than to the sprite, because a PAM has no scale of its own
    // and its parts would each have to be scaled about the right point.
    public static void drawStandingScaled(Batch batch, EntitySprite sprite, String clip,
                                          float stateTime, float centreX, float footY,
                                          boolean faceRight, Map<String, Boolean> parts,
                                          float scale) {
        if (scale == 1f) {
            drawStanding(batch, sprite, clip, stateTime, centreX, footY, faceRight, parts);
            return;
        }
        com.badlogic.gdx.math.Matrix4 previous = batch.getTransformMatrix().cpy();
        // Translate to the FOOT point first, then scale about it, so the sprite shrinks toward the
        // ground it is standing on rather than toward the origin of the board.
        batch.setTransformMatrix(new com.badlogic.gdx.math.Matrix4(previous)
                .translate(toSpriteSpace(centreX), toSpriteSpace(footY), 0f)
                .scale(scale, scale, 1f));
        sprite.draw(batch, clip, stateTime, 0f, -bottomOffset(sprite, clip), faceRight, parts);
        batch.setTransformMatrix(previous);
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
    // Package-private rather than private: ZombotanyHead has to rebuild the exact origin a body was
    // drawn from in order to hang a head off it, and re-deriving that arithmetic there would be two
    // copies of the y-down flip to keep in step.
    static float bottomOffset(EntitySprite sprite, String clip) {
        // anchorBounds(), not bounds(clip): see EntitySprite.anchorBounds for why using the playing
        // clip's box made walking zombies float a lane above the ground.
        Rectangle bounds = sprite.anchorBounds();
        return bounds == null ? 0f : -(bounds.y + bounds.height);
    }
}
