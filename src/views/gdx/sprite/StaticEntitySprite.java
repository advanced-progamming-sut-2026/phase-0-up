package views.gdx.sprite;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.Map;

// The fallback: one still image, drawn for every clip.
//
// Used when an entity has no .PAM in the asset dump at all (Rotobaga, Cat-tail, Iceberg Lettuce,
// Kernel-pult and a few others are genuinely absent) or when its animation failed to load. The game
// stays playable and the entity stays visible; it just does not move. That is the whole reason
// renderers go through EntitySprite instead of calling libPVZ directly.
//
// A null region is legal and draws nothing -- better than a crash or a magenta box in a submitted
// build -- but reports isReady() == false so a debug overlay can list what is missing.
final class StaticEntitySprite implements EntitySprite {

    private final TextureRegion region;
    private final float width;
    private final float height;

    StaticEntitySprite(TextureRegion region) {
        this.region = region;
        this.width = region != null ? region.getRegionWidth() : 0f;
        this.height = region != null ? region.getRegionHeight() : 0f;
    }

    @Override
    public void draw(Batch batch, String clip, float stateTime, float x, float y, boolean faceRight) {
        draw(batch, clip, stateTime, x, y, faceRight, null);
    }

    @Override
    public void draw(Batch batch, String clip, float stateTime, float x, float y, boolean faceRight,
                     Map<String, Boolean> parts) {
        if (region == null) {
            return;
        }
        // Drawn centred horizontally and sitting on y, to match how PamPlayer anchors its output --
        // so swapping an entity between the two paths does not shift it on the lawn.
        float drawX = x - width / 2f;
        float drawWidth = faceRight ? width : -width;
        float originX = faceRight ? 0f : width;
        batch.draw(region, drawX + originX, y, drawWidth, height);
    }

    @Override
    public boolean isReady() {
        return region != null;
    }

    @Override
    public boolean isAnimated() {
        return false;
    }

    // A still image is the same picture whatever the requested clip, so it never refuses one. Saying
    // "yes" here stops callers from walking a fallback chain that would land back on this same image.
    @Override
    public boolean hasClip(String clip) {
        return region != null;
    }

    // No named clips at all: hasClip already says yes to everything, so a caller hunting for a resting
    // pose gets its first choice and never reaches the last-resort list.
    @Override
    public java.util.Set<String> clips() {
        return java.util.Set.of();
    }

    // A flat image has no parts to toggle, so armor and status overlays simply do not appear on the
    // fallback path. The entity is still visible, which is the point.
    @Override
    public boolean hasPart(String partName) {
        return false;
    }

    // Reported in the .PAM's own y-DOWN convention, not this class's y-up one.
    //
    // Every caller reads bounds the libPVZ way: the art hangs BELOW the origin, so its vertical span is
    // [y, y + height] measured downward and callers centre on `y + height/2`. Returning y = 0 -- which
    // is where draw() actually starts the image -- made that formula resolve to +height/2, so a still
    // was drawn half its own height too high: Knight Zombie's card art climbed out of its tile and over
    // the row above it, and on the lawn a still-image entity floated.
    //
    // y = -height puts the same rectangle in the convention everyone else is already using.
    @Override
    public com.badlogic.gdx.math.Rectangle bounds(String clip) {
        if (region == null) {
            return null;
        }
        return new com.badlogic.gdx.math.Rectangle(-width / 2f, -height, width, height);
    }

    // A still image is one part, so there is nothing to hide and nothing to recompute.
    @Override
    public com.badlogic.gdx.math.Rectangle visibleBounds(String clip, java.util.Set<String> hidden) {
        return bounds(clip);
    }

    // A still has no named parts at all, so nothing can be asked about one.
    @Override
    public com.badlogic.gdx.math.Rectangle partBounds(String clip, String partName) {
        return null;
    }

    @Override
    public com.badlogic.gdx.math.Rectangle[] partBoundsByFrame(String clip, String partName) {
        return null;
    }

    // A still image never advances, so it has no duration.
    @Override
    public float clipDuration(String clip) {
        return 0f;
    }

    // A still image has one pose, so its anchor is simply its own box.
    @Override
    public com.badlogic.gdx.math.Rectangle anchorBounds() {
        return bounds(null);
    }
}
