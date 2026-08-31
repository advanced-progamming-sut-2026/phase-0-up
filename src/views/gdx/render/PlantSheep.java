package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import models.entities.plants.Plant;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

// The sheep a Wizard Zombie's hex leaves behind.
//
// A hexed plant used to be drawn as an ordinary plant that had quietly stopped working, which is the
// worst possible reading of it: the player sees a Peashooter standing in a lane doing nothing and
// concludes the plant is broken rather than that a wizard needs killing.
//
// This REPLACES the plant rather than sitting on top of it, which is where it differs from PlantOctopus
// next door -- an octopus is something clinging to a plant, and a sheep is what the plant has become.
// PlantRenderer hands over the whole tile and draws nothing else on it.
//
// DARK_WIZARD_SHEEPENING ships the transformation and the result in one animation: `animation` is the
// 1.7s poof, then `idle`/`idle2`/`idle3` are the sheep standing there. Both halves are played, in that
// order, because the poof is the only thing that tells the player what just happened to their plant.
final class PlantSheep {

    private static final String SPRITE = "DARK_WIZARD_SHEEPENING";
    private static final String ARRIVAL_CLIP = "animation";
    private static final String[] STANDING_CLIPS = {"idle", "idle2", "idle3"};

    // A sheep is a whole plant's worth of tile, and it stands on the ground rather than floating at the
    // plant's midpoint -- so, unlike the octopus overlay, this is a bit wider and sits lower.
    private static final float WIDTH_CELLS = 1.0f;
    private static final float LIFT_CELLS = 0.34f;

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final LocalTransform transform = new LocalTransform();

    // How long each plant has been a sheep. Identity-keyed, like every other per-entity map in this
    // package: two plants are never "equal" for anything that matters and Plant does not override it.
    //
    // Needed because the arrival poof plays ONCE and the standing loop runs forever after it, and
    // nothing in the model records when the hex landed -- only that it is in force.
    private final java.util.Map<Plant, Float> since = new java.util.IdentityHashMap<>();

    PlantSheep(SpriteRegistry sprites, LawnGeometry lawn) {
        this.sprites = sprites;
        this.lawn = lawn;
    }

    // Once a frame, from PlantRenderer's sweep. Ages every sheep and drops the plants that are no
    // longer under the hex -- the wizard died, or the plant did.
    void advance(float delta) {
        since.entrySet().removeIf(entry -> !entry.getKey().isSheep() || entry.getKey().isDead());
        for (java.util.Map.Entry<Plant, Float> entry : since.entrySet()) {
            entry.setValue(entry.getValue() + delta);
        }
    }

    void draw(Batch batch, Plant plant, float cx, float footY) {
        EntitySprite sheep = sprites.get(SPRITE);
        if (sheep == null || !sheep.isReady()) {
            return;
        }
        float elapsed = since.computeIfAbsent(plant, p -> 0f);
        float poof = sheep.clipDuration(ARRIVAL_CLIP);

        String clip;
        float stateTime;
        if (sheep.hasClip(ARRIVAL_CLIP) && elapsed < poof) {
            clip = ARRIVAL_CLIP;
            stateTime = ClipMap.sample(sheep, clip, elapsed);
        } else {
            clip = ClipMap.firstAvailable(sheep, STANDING_CLIPS);
            // Offset per tile so a row of hexed plants does not chew in unison -- the same trick
            // PlantOctopus uses, and for the same reason: a shared clock with no phase reads as one
            // sheep drawn five times.
            float phase = (float) (plant.getX() * 0.37 + plant.getY() * 0.61);
            stateTime = ClipMap.sample(sheep, clip, elapsed - poof + phase);
        }

        com.badlogic.gdx.math.Rectangle bounds = sheep.bounds(clip);
        if (bounds == null || bounds.width <= 0f) {
            return;
        }
        float scale = SpritePlacer.toSpriteSpace(WIDTH_CELLS * lawn.cellWidth()) / bounds.width;
        float centreY = footY + lawn.cellHeight() * LIFT_CELLS;

        float previous = batch.getPackedColor();
        batch.setColor(Color.WHITE);
        transform.begin(batch, SpritePlacer.toSpriteSpace(cx),
                SpritePlacer.toSpriteSpace(centreY), scale);
        // Centred on the art's own box in BOTH axes. The y term is the usual y-down flip; the x term
        // matters because effect animations are not reliably authored around their origin the way a
        // character is, and one that is not would otherwise sit half a box off its tile.
        sheep.draw(batch, clip, stateTime,
                -(bounds.x + bounds.width / 2f), bounds.y + bounds.height / 2f, true);
        transform.end(batch);
        batch.setPackedColor(previous);
    }
}
