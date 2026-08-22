package views.gdx.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import models.game.EnvironmentType;
import views.gdx.core.Assets;

// Draws the lawn background for a world.
//
// Every world's background is shipped as three horizontal slices rather than one image, because a
// single 1975x768 texture would not fit an atlas page:
//
//     [ _LEFT 278 ][ _TEXTURE 1024 ][ _RIGHT 673 ]      = 1975 x 768
//
// (Frostbite is 281/1022/673 -- close but not identical, so the pieces are laid out by their ACTUAL
// widths rather than by constants, and a new world needs no new numbers here.)
//
// World space is these background pixels. Nothing is scaled or stretched: the camera decides how much
// of it is on screen, which is what keeps the aspect ratio honest as the window resizes.
public final class BackgroundRenderer {

    private final Assets assets;

    private TextureRegion left;
    private TextureRegion middle;
    private TextureRegion right;
    private EnvironmentType loadedFor;
    private float totalWidth;

    public BackgroundRenderer(Assets assets) {
        this.assets = assets;
    }

    // Resolves the three slices for a world. Cheap to call repeatedly -- it no-ops unless the world
    // actually changed.
    public void load(EnvironmentType environment) {
        if (environment == loadedFor) {
            return;
        }
        String key = atlasKey(environment);
        left = region("IMAGE_BACKGROUNDS_" + key + "_TEXTURE_LEFT");
        middle = region("IMAGE_BACKGROUNDS_" + key + "_TEXTURE");
        right = region("IMAGE_BACKGROUNDS_" + key + "_TEXTURE_RIGHT");

        totalWidth = width(left) + width(middle) + width(right);
        loadedFor = environment;

        Gdx.app.log("BackgroundRenderer", key + " background: " + (int) totalWidth + "x"
                + (int) (middle != null ? middle.getRegionHeight() : 0));
    }

    // Our EnvironmentType names are the game's; the art uses the original PopCap world names.
    private static String atlasKey(EnvironmentType environment) {
        if (environment == null) {
            return "EGYPT";
        }
        return switch (environment) {
            case ANCIENT_EGYPT -> "EGYPT";
            case FROSTBITE_CAVES -> "ICEAGE";
            case BIG_WAVE_BEACH -> "BEACH";
            case DARK_AGES -> "DARK";
        };
    }

    private TextureRegion region(String id) {
        try {
            return assets.region(id);
        } catch (RuntimeException e) {
            Gdx.app.error("BackgroundRenderer", "missing background slice " + id, e);
            return null;
        }
    }

    private static float width(TextureRegion region) {
        return region == null ? 0f : region.getRegionWidth();
    }

    public void draw(Batch batch) {
        float x = 0f;
        x += drawSlice(batch, left, x);
        x += drawSlice(batch, middle, x);
        drawSlice(batch, right, x);
    }

    // The three slices are TOP-aligned, not bottom-aligned.
    //
    // `[M]` This matters for exactly one world. Egypt, Beach and Dark Ages ship all three slices at
    // 768 tall, so the distinction is invisible; ICEAGE's middle slice is **785** against its sides' 768,
    // and drawing everything at y=0 aligned the bottoms and pushed the middle 17px up. The result was a
    // hard vertical step at both seams -- the ice cracks and the lawn's white rim visibly did not meet
    // the side slices -- which reads as "the middle of the background is too high", because it was.
    //
    // Verified by cropping the middle/right seam out of a render at both alignments: bottom-aligned has
    // an obvious break in the crack pattern, top-aligned is seamless. So the extra 17px is content at the
    // BOTTOM of the middle slice, and the tops are what line up.
    //
    // Anchored to WORLD_HEIGHT rather than to max(slice heights): 768 is the nominal height every world's
    // sides are authored at and the height the camera frames, so this is a no-op for the three worlds
    // that were already right, instead of shifting their sides up by 17.
    private static float drawSlice(Batch batch, TextureRegion region, float x) {
        if (region == null) {
            return 0f;
        }
        // Native size, no scaling: world units ARE background pixels, which is the property that keeps
        // the lawn welded to the art.
        batch.draw(region, x, views.gdx.map.LawnGeometry.WORLD_HEIGHT - region.getRegionHeight());
        return region.getRegionWidth();
    }

    public float totalWidth() {
        return totalWidth;
    }

    public float height() {
        return middle == null ? 0f : middle.getRegionHeight();
    }
}
