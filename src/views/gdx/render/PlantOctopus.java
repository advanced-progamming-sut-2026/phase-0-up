package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import models.entities.plants.Plant;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

// The octopus clinging to a snared plant (T8.4).
//
// A snared plant used to get a flat purple tint and nothing else, which read as poisoned rather than as
// grabbed -- and left T8.4 with no octopus to flash, because there was no octopus. The dump ships one,
// so it is drawn instead: an orange octopus clinging to the plant, over the top of it.
//
// `animation3` is the 3s clinging loop; `animation` is the 0.3s throw and `die` the 2s release, neither
// of which is the state a snared plant is in.
//
// Split out of PlantRenderer, which is at its size limit: this is a self-contained overlay with its own
// clock and its own damage flash, and nothing else in the renderer touches either.
final class PlantOctopus {

    private static final String SPRITE = "ZOMBIE_OCTOPUS_PROJECTILE";
    private static final String[] CLIPS = {"animation3", "animation4", "animation2"};

    // Drawn a shade wider than the plant it has hold of, so the tentacles read as wrapping round it
    // rather than sitting on it, and lifted to the plant's middle rather than its feet.
    private static final float WIDTH_CELLS = 0.95f;
    private static final float LIFT_CELLS = 0.42f;

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final LocalTransform transform = new LocalTransform();

    // Keyed on the PLANT, in a DamageFlash of its own. The plant is the only stable identity the
    // octopus has -- it is not an entity in the model, just two fields on the plant it grabbed -- and a
    // second instance is what keeps its hits from being confused with the plant's own: Projectile.onHit
    // sends a shot into damageOctopus while the plant underneath is untouched, so flashing the plant
    // would credit the hit to the wrong thing, and the plant, being undamaged, would never flash at all
    // while the player shot the octopus off it.
    private final DamageFlash flashes = new DamageFlash();
    private float clock;

    PlantOctopus(SpriteRegistry sprites, LawnGeometry lawn) {
        this.sprites = sprites;
        this.lawn = lawn;
    }

    // Once a frame, from PlantRenderer.sweepFlashes. Advancing the clock inside draw() would run it
    // once per snared plant per frame.
    void advance(float delta) {
        flashes.sweep();
        clock += delta;
    }

    void draw(Batch batch, Plant plant, float cx, float footY, float delta) {
        EntitySprite octopus = sprites.get(SPRITE);
        if (octopus == null || !octopus.isReady()) {
            return;
        }
        String clip = ClipMap.firstAvailable(octopus, CLIPS);
        // One shared clock plus a per-plant phase derived from the tile it is on. AnimationClocks is an
        // IdentityHashMap, so a per-octopus key would have to be an object that survives between
        // frames -- and a stable offset off the coordinates gives the same "they do not writhe in
        // unison" result with no state at all.
        float phase = (float) (plant.getX() * 0.37 + plant.getY() * 0.61);
        float stateTime = ClipMap.sample(octopus, clip, clock + phase);
        float centreY = footY + lawn.cellHeight() * LIFT_CELLS;

        com.badlogic.gdx.math.Rectangle bounds = octopus.bounds(clip);
        if (bounds == null || bounds.width <= 0f) {
            return;
        }
        float scale = SpritePlacer.toSpriteSpace(WIDTH_CELLS * lawn.cellWidth()) / bounds.width;

        float previous = batch.getPackedColor();
        batch.setColor(Color.WHITE);
        drawAt(batch, octopus, clip, stateTime, cx, centreY, scale, bounds);

        float flash = flashes.intensity(plant, plant.getOctopusHp(), delta);
        if (flash > 0f) {
            SpritePlacer.beginAdditive(batch);
            batch.setColor(flash, flash, flash, 1f);
            drawAt(batch, octopus, clip, stateTime, cx, centreY, scale, bounds);
            SpritePlacer.endAdditive(batch);
        }
        batch.setPackedColor(previous);
    }

    private void drawAt(Batch batch, EntitySprite octopus, String clip, float stateTime,
                        float cx, float centreY, float scale,
                        com.badlogic.gdx.math.Rectangle bounds) {
        transform.begin(batch, SpritePlacer.toSpriteSpace(cx),
                SpritePlacer.toSpriteSpace(centreY), scale);
        octopus.draw(batch, clip, stateTime, 0f, bounds.y + bounds.height / 2f, true);
        transform.end(batch);
    }
}
