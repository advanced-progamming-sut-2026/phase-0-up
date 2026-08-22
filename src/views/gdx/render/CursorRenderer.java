package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import views.gdx.input.ToolState;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;
import views.gdx.ui.UiArt;

// Draws whatever the cursor is carrying, at the cursor.
//
// This is T2.3, and it exists because a tile highlight alone does not answer the question the player is
// actually asking: not "which square" but "which plant, and is this where I want it". Carrying the
// plant's own idle animation answers both at once -- it is the same art that will be standing there a
// moment later, so there is nothing to translate mentally.
//
// Ghosted rather than opaque, so it reads as a preview and not as an already-planted plant.
public final class CursorRenderer {

    private static final float GHOST_ALPHA = 0.65f;

    // The held plant is drawn at the size it will be once planted, so the preview does not lie about
    // how much of the tile it fills.
    private static final float PLANT_WIDTH_CELLS = 0.95f;
    private static final float TOOL_WIDTH_CELLS = 0.7f;

    private final SpriteRegistry sprites;
    private final UiArt art;
    private final LawnGeometry lawn;

    private float stateTime;

    public CursorRenderer(SpriteRegistry sprites, UiArt art, LawnGeometry lawn) {
        this.sprites = sprites;
        this.art = art;
        this.lawn = lawn;
    }

    public void draw(Batch batch, ToolState tools, float worldX, float worldY, float delta) {
        stateTime += delta;
        switch (tools.tool()) {
            case SEED -> drawEntity(batch, tools.seedName(), worldX, worldY, null);
            // A held nut is drawn as the nut itself, through the same path a held plant is: what the
            // player is carrying IS a plant, it is simply about to be rolled rather than placed.
            case NUT -> drawEntity(batch, BowlingRenderer.spriteFor(
                    BowlingRenderer.kindFor(tools.nutToken())), worldX, worldY, null);
            // And a held zombie, in I, Zombie. With its armour switched on: the roster sells a
            // Buckethead for three times a Browncoat, and a ghost with no bucket is a ghost of the
            // wrong purchase.
            case ZOMBIE -> drawZombie(batch, tools.zombieAlias(), worldX, worldY);
            case SHOVEL -> drawTool(batch, UiArt.SHOVEL_ICON, worldX, worldY);
            case PLANT_FOOD -> drawTool(batch, UiArt.PLANTFOOD_BUTTON, worldX, worldY);
            default -> { }
        }
    }

    private void drawZombie(Batch batch, String alias, float worldX, float worldY) {
        if (alias == null) {
            return;
        }
        EntitySprite sprite = sprites.get(alias);
        drawEntity(batch, alias, worldX, worldY,
                views.gdx.sprite.ArmorVisibility.forAlias(alias, sprite));
    }

    // parts may be null, which is every plant: only zombies have hideable armour.
    private void drawEntity(Batch batch, String entityName, float worldX, float worldY,
                            java.util.Map<String, Boolean> parts) {
        if (entityName == null) {
            return;
        }
        EntitySprite sprite = sprites.get(entityName);
        if (sprite == null || !sprite.isReady()) {
            return;
        }
        // Not ClipMap.firstAvailable: the staged plants have no plain "idle", so the ghost of a
        // Sun-shroom or a Puff-shroom was an invisible cursor.
        String clip = views.gdx.sprite.PlantStages.restingClip(sprite);
        Rectangle bounds = sprite.bounds(clip);
        if (bounds == null || bounds.width <= 0f) {
            return;
        }
        float target = SpritePlacer.toSpriteSpace(PLANT_WIDTH_CELLS * lawn.cellWidth());
        float scale = target / bounds.width;

        float previousColor = batch.getPackedColor();
        Color tint = batch.getColor();
        batch.setColor(tint.r, tint.g, tint.b, tint.a * GHOST_ALPHA);

        Matrix4 previous = batch.getTransformMatrix().cpy();
        Matrix4 scaled = new Matrix4(previous)
                .translate(SpritePlacer.toSpriteSpace(worldX), SpritePlacer.toSpriteSpace(worldY), 0f)
                .scale(scale, scale, 1f);
        batch.setTransformMatrix(scaled);
        // Same y-down correction as everywhere else -- the art hangs below the .PAM origin.
        sprite.draw(batch, clip, ClipMap.sample(sprite, clip, stateTime),
                0f, bounds.y + bounds.height / 2f, true, parts);
        batch.setTransformMatrix(previous);

        batch.setPackedColor(previousColor);
    }

    // Tools are flat UI images, not animations, so they are drawn straight rather than through a PAM.
    private void drawTool(Batch batch, String regionId, float worldX, float worldY) {
        TextureRegion region = art.region(regionId);
        if (region == null) {
            return;
        }
        float w = SpritePlacer.toSpriteSpace(TOOL_WIDTH_CELLS * lawn.cellWidth());
        float h = w * region.getRegionHeight() / (float) region.getRegionWidth();

        float previousColor = batch.getPackedColor();
        Color tint = batch.getColor();
        batch.setColor(tint.r, tint.g, tint.b, tint.a * GHOST_ALPHA);
        batch.draw(region, SpritePlacer.toSpriteSpace(worldX) - w / 2f,
                SpritePlacer.toSpriteSpace(worldY) - h / 2f, w, h);
        batch.setPackedColor(previousColor);
    }
}
