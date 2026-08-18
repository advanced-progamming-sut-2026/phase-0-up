package views.gdx.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.PlantStages;

// One entity, alive, standing on a patch of lawn.
//
// The almanac's centrepiece: rather than a still thumbnail, the selected plant or zombie plays its
// resting animation on a scrap of the same ground it fights on. That is the whole difference between a
// catalogue and a display case.
//
// It is NOT EntityIcon with a background. EntityIcon fits art into a UI box and is used forty times at
// once in the grid, where a shared clock and a tight fit are what matter. This is a single, larger
// view that anchors the entity's FEET to the turf -- so a Gargantuar towers out of its tile and a
// Sun-shroom sits low in it, the same relationship they have on the lawn -- and runs its own clock so
// the featured entity does not step in time with the forty behind it.
public final class LiveEntityActor extends Actor {

    // The shipped grass patch is 179x50 -- wider than it is tall, and meant to sit UNDER a plant. Drawn
    // to a fraction of the actor rather than stretched across it, so it reads as a patch of lawn the
    // entity is standing on and not as a shelf it is standing behind.
    private static final float TURF_WIDTH_FRACTION = 0.62f;
    private static final float TURF_FRACTION = 0.17f;
    // How much of the remaining height the entity is scaled to fill. Under 1 so a tall zombie has
    // headroom rather than being cropped at the panel's edge.
    private static final float FIT = 0.82f;
    // Where on the turf the feet land: a little forward of its back edge, the same inset the lawn uses
    // so a plant reads as standing ON the ground rather than behind it.
    private static final float FOOT_INSET = 0.35f;

    private final TextureRegion turf;

    private EntitySprite sprite;
    private String clip;
    private Rectangle bounds;
    private float stateTime;

    // Which optional parts to switch on -- a zombie's cone, bucket or brick. Null draws the animation's
    // default set, which for the shared zombie body means a bare head.
    private java.util.Map<String, Boolean> parts;

    public LiveEntityActor(TextureRegion turf) {
        this.turf = turf;
    }

    public void show(EntitySprite sprite) {
        show(sprite, null);
    }

    // Swaps the subject. The clock restarts, so a newly selected entity is seen from the beginning of
    // its animation rather than joining the previous one's cycle wherever it happened to be.
    //
    // parts switches optional pieces on. Conehead, Buckethead and Brick share one animation carrying all
    // three hats, so this is the only thing that tells them apart -- see ArmorVisibility.
    public void show(EntitySprite sprite, java.util.Map<String, Boolean> parts) {
        this.sprite = sprite;
        this.clip = sprite == null ? null : PlantStages.restingClip(sprite);
        this.parts = parts;
        // Measured against what is DRAWN, not what the animation could draw. bounds() folds in every
        // switched-off part, so a bare zombie carries the tallest hat it could wear and stands four
        // fifths of the height it should, low in its frame. See EntitySprite.visibleBounds.
        this.bounds = sprite == null || clip == null ? null
                : sprite.visibleBounds(clip, views.gdx.sprite.ArmorVisibility.hiddenParts(sprite, parts));
        this.stateTime = 0f;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        stateTime += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        // No turf means no turf ROOM either: a plant in a greenhouse pot stands on soil the pot already
        // draws, so reserving a seventh of the box for absent grass would only sink it.
        float turfHeight = turf == null ? 0f : getHeight() * TURF_FRACTION;
        if (turf != null) {
            float turfWidth = getWidth() * TURF_WIDTH_FRACTION;
            com.badlogic.gdx.graphics.Color previous = batch.getColor().cpy();
            batch.setColor(previous.r, previous.g, previous.b, previous.a * parentAlpha);
            batch.draw(turf, getX() + (getWidth() - turfWidth) / 2f, getY(), turfWidth, turfHeight);
            batch.setColor(previous);
        }
        if (bounds == null || bounds.width <= 0f || bounds.height <= 0f) {
            return;
        }

        // Fitted against the space ABOVE the turf, not the whole actor, so the turf is scenery rather
        // than something the entity has to share its height with.
        float room = getHeight() - turfHeight;
        float scale = Math.min(getWidth() / bounds.width, room / bounds.height) * FIT;

        float feetY = getY() + turfHeight * FOOT_INSET;
        Matrix4 previous = batch.getTransformMatrix().cpy();
        batch.setTransformMatrix(new Matrix4(previous)
                .translate(getX() + getWidth() / 2f, feetY, 0f)
                .scale(scale, scale, 1f));
        // Anchored by the BOTTOM of the art. libPVZ reports bounds in the .PAM's own Flash-style
        // coordinates, where the rectangle runs DOWNWARD from y -- so in GDX's y-up space the art
        // occupies [o - (y + height), o - y] about the draw origin o. Putting its bottom edge on the
        // foot line means o = y + height.
        //
        // NOT -(y + height), which is the same magnitude the other way and sinks the entity by twice
        // its own height: that is what buried the plants and zombies under the dirt.
        sprite.draw(batch, clip, ClipMap.sample(sprite, clip, stateTime),
                0f, bounds.y + bounds.height, true, parts);
        batch.setTransformMatrix(previous);
    }
}
