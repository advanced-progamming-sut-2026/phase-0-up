package views.gdx.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;

// A plant drawn small, inside a UI widget.
//
// The art is skeletal and authored at lawn scale -- a Peashooter's idle box is about 100x150 PAM units,
// several times a seed card. There is no smaller version and no static thumbnail in the asset dump, so
// the same animation is drawn through a scale-to-fit transform.
//
// A transform rather than a scaled draw call because libPVZ's scaled overloads take a scale but not a
// visibility map, and applying it here keeps one code path for every icon regardless.
public final class PlantIcon extends Actor {

    // Leaves a little air around the art so it does not touch the card's edges.
    private static final float FIT = 0.88f;

    private final EntitySprite sprite;
    private final String clip;
    private final Rectangle bounds;
    private float stateTime;

    public PlantIcon(EntitySprite sprite) {
        this.sprite = sprite;
        this.clip = ClipMap.firstAvailable(sprite, ClipMap.IDLE);
        this.bounds = sprite.bounds(clip);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        // Seed cards idle-animate in the original, and it costs nothing: the clock is per icon, so a
        // bank of eight does not step in unison.
        stateTime += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (bounds == null || bounds.width <= 0f || bounds.height <= 0f) {
            return;
        }
        float scale = Math.min(getWidth() / bounds.width, getHeight() / bounds.height) * FIT;

        Matrix4 previous = batch.getTransformMatrix().cpy();
        Matrix4 scaled = new Matrix4(previous)
                .translate(getX() + getWidth() / 2f, getY() + getHeight() / 2f, 0f)
                .scale(scale, scale, 1f);

        // Changing the transform flushes the batch, so this is done once per icon rather than per part.
        batch.setTransformMatrix(scaled);
        // Same y-down correction as SpritePlacer.drawCentred: libPVZ reports bounds in the .PAM's own
        // Flash-style coordinates, where the art hangs BELOW the origin.
        sprite.draw(batch, clip, ClipMap.sample(sprite, clip, stateTime),
                0f, bounds.y + bounds.height / 2f, true);
        batch.setTransformMatrix(previous);
    }
}
