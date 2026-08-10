package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import models.game.EnvironmentType;
import models.map.Cell;
import models.map.Terrains.GraveInDarkAgesTerrain;
import models.map.Terrains.GraveTerrain;
import models.map.Terrains.Terrain;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

// Draws whatever is sitting ON a tile rather than planted in it: headstones today, and the ice, water
// and marked tiles the later worlds need.
//
// Graves are the urgent case. Ancient Egypt's very first level places them, the terminal build shows
// them as '#', and until this class existed the GUI drew nothing at all -- so a tile you could not
// plant on looked completely ordinary.
//
// The art carries damage states as clips (undamaged, damage1..damage4), and GraveTerrain already
// tracks hp against a max, so a headstone visibly crumbles as it is shot for free. That is the
// "tomb degradation" item from the spec's aesthetics list.
public final class TerrainRenderer {

    private static final String[] DAMAGE_CLIPS = {
            "undamaged", "damage1", "damage2", "damage3", "damage4"
    };

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final EnvironmentType environment;
    private final AnimationClocks clocks;

    public TerrainRenderer(SpriteRegistry sprites, LawnGeometry lawn, AnimationClocks clocks,
                           EnvironmentType environment) {
        this.sprites = sprites;
        this.lawn = lawn;
        this.clocks = clocks;
        this.environment = environment;
    }

    public void drawCell(Batch batch, Cell cell, int col, int row, float delta) {
        if (cell.getTerrain() == null || cell.getTerrain().isEmpty()) {
            return;
        }
        for (Terrain terrain : cell.getTerrain()) {
            if (terrain.isDestroyed()) {
                continue;   // removed from the cell at the end of the tick; do not draw a broken stone
            }
            if (terrain instanceof GraveTerrain grave) {
                drawGrave(batch, grave, col, row, delta);
            }
            // Ice blocks, water, slider tiles and necromancy markers join here in T7.7, once those
            // worlds are reachable.
        }
    }

    private void drawGrave(Batch batch, GraveTerrain grave, int col, int row, float delta) {
        EntitySprite sprite = sprites.get(graveSpriteName(grave));
        String clip = ClipMap.firstAvailable(sprite, damageClipFor(grave));
        float stateTime = ClipMap.sample(sprite, clip, clocks.advance(grave, clip, delta));

        SpritePlacer.drawStanding(batch, sprite, clip, stateTime,
                lawn.centerX(col),
                lawn.worldY(row) + lawn.cellHeight() * SpritePlacer.FOOT_INSET,
                true, null);
    }

    // Dark Ages headstones come in three flavours and the spec explicitly asks that they look
    // different; the art happens to ship exactly those three. Everywhere else the world decides.
    private String graveSpriteName(GraveTerrain grave) {
        if (grave instanceof GraveInDarkAgesTerrain dark) {
            return switch (dark.getType()) {
                case SUNNY -> "DARK_SUN";
                case FOODY -> "DARK_PLANTFOOD";
                case PLAIN -> "DARK_NOOP";
            };
        }
        if (environment == null) {
            return "TUTORIAL_GRAVESTONE";
        }
        return switch (environment) {
            case ANCIENT_EGYPT -> "EGYPT_HIEROGLYPH";
            case DARK_AGES -> "DARK_NOOP";
            // Frostbite and the beach have no headstones of their own in this dump; the plain
            // gravestone reads correctly on any ground.
            case FROSTBITE_CAVES, BIG_WAVE_BEACH -> "TUTORIAL_GRAVESTONE";
        };
    }

    // Five art states across the headstone's health, so it crumbles as it is shot.
    private static String damageClipFor(GraveTerrain grave) {
        int max = Math.max(1, grave.getMaxHp());
        float remaining = grave.getHp() / (float) max;
        // The LAST clip is the headstone already gone -- rubble, or nothing at all. Spreading the
        // health range across every clip therefore made a nearly-dead grave render as empty ground
        // while it was still standing and still blocking shots. A grave that is alive is always drawn
        // in a state you can see; only isDestroyed() removes it.
        int usable = DAMAGE_CLIPS.length - 1;          // exclude the vanished pose
        int index = Math.round((1f - remaining) * (usable - 1));
        return DAMAGE_CLIPS[Math.max(0, Math.min(usable - 1, index))];
    }
}
