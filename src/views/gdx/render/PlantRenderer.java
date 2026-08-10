package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import models.entities.plants.Plant;
import models.map.Cell;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

import java.util.IdentityHashMap;
import java.util.Map;

// Draws the plants of one lane.
//
// A tile can hold three plants at once and the order they are drawn in is not cosmetic -- it is the
// same order zombies eat through them (Cell.getDefendingPlant): a Lily Pad is the platform underneath,
// the real plant sits on it, and a Pumpkin is the shell in front.
public final class PlantRenderer {

    // How long a shooter stays in its "attack" clip after firing. The model has no "is attacking"
    // flag -- shooting is instantaneous there -- so the view keeps this pulse itself, started by
    // GameRenderer the frame a new projectile from this plant appears.
    // Fallback only. The pulse actually lasts the attack clip's own length (Peashooter's is 1.03s),
    // read from the animation, so the shot animation is never cut off partway through.
    private static final float ATTACK_SECONDS = 0.45f;

    // Crossfade back to idle. Without it the plant snaps from its final attack pose to the idle pose
    // in a single frame, which reads as a twitch.
    private static final float BLEND_SECONDS = 0.18f;

    // Frozen plants are encased in ice. Phase 1 tints rather than drawing an ice block; the block
    // itself is Frostbite Caves work (T7.7). Three steps, matching Plant.getChillLevel()'s 1..3.
    private static final Color[] CHILL_TINT = {
            Color.WHITE,
            new Color(0.80f, 0.92f, 1f, 1f),
            new Color(0.62f, 0.84f, 1f, 1f),
            new Color(0.45f, 0.76f, 1f, 1f),
    };

    private static final Color OCTOPUS_TINT = new Color(0.85f, 0.6f, 0.85f, 1f);

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final AnimationClocks clocks;

    // Remaining attack-pulse seconds per plant.
    private final Map<Plant, Float> attacking = new IdentityHashMap<>();
    // Remaining crossfade seconds per plant, started when its attack pulse ends.
    private final Map<Plant, Float> blending = new IdentityHashMap<>();

    // NOTE: there was an attempt here to predict the next shot from the plant's actionInterval and
    // start the attack clip early, so its last frame would land on the pea. It is deliberately gone.
    // The prediction and the post-shot pulse below both selected the attack clip, so the animation
    // played TWICE per cycle -- once before the shot and once after. Making a plant's animation lead
    // its shot needs the MODEL to announce an imminent attack; it cannot be inferred in the view.

    public PlantRenderer(SpriteRegistry sprites, LawnGeometry lawn, AnimationClocks clocks) {
        this.sprites = sprites;
        this.lawn = lawn;
        this.clocks = clocks;
    }

    // Called when a projectile fired by this plant is first seen, so it can play its shot animation.
    public void noteShot(Plant shooter) {
        if (shooter == null) {
            return;
        }
        EntitySprite sprite = sprites.get(shooter.getName());
        String shot = ClipMap.firstAvailable(sprite, "attack", "special", "shooting");
        float length = ClipMap.IDLE.equals(shot) ? ATTACK_SECONDS : sprite.clipDuration(shot);
        attacking.put(shooter, length > 0f ? length : ATTACK_SECONDS);
        blending.remove(shooter);
    }

    // Redraws one plant on top of whatever has already been drawn, WITHOUT advancing its clock (the
    // main pass already did that this frame). Used to put a shooter back over its own projectile.
    public void redraw(Batch batch, Plant plant) {
        if (plant == null || plant.isDead()) {
            return;
        }
        int col = (int) Math.floor(plant.getX());
        int row = plant.getY();
        if (col < 0 || col >= utils.Constants.BOARD_COLS
                || row < 0 || row >= utils.Constants.BOARD_ROWS) {
            return;
        }
        draw(batch, plant, col, row, 0f);
    }

    public void drawCell(Batch batch, Cell cell, int col, int row, float delta) {
        // Bottom of the stack first. A dead-but-not-yet-swept plant is skipped: it stays in its cell
        // until the end of the tick it died on, and drawing it would show a corpse standing.
        draw(batch, cell.getPlatform(), col, row, delta);
        draw(batch, cell.getCurrentPlant(), col, row, delta);
        draw(batch, cell.getProtector(), col, row, delta);
    }

    private void draw(Batch batch, Plant plant, int col, int row, float delta) {
        if (plant == null || plant.isDead()) {
            return;
        }
        EntitySprite sprite = sprites.get(plant.getName());
        String clip = clipFor(sprite, plant, delta);
        float stateTime = ClipMap.sample(sprite, clip, clocks.advance(plant, clip, delta));

        Color previous = batch.getColor().cpy();
        Color tint = tintFor(plant);
        float cx = lawn.centerX(col);
        float fy = footY(row);

        // Crossfade out of the attack pose rather than cutting. Both clips are drawn for a moment,
        // their opacities summing to 1, which blends the poses without needing a shader.
        Float fade = blending.get(plant);
        if (fade != null) {
            float left = fade - delta;
            if (left > 0f) {
                blending.put(plant, left);
            } else {
                blending.remove(plant);
            }
            float t = 1f - Math.max(0f, left) / BLEND_SECONDS;   // 0 -> just finished, 1 -> fully idle

            // Idle FIRST and fully opaque, with the attack pose fading out on top of it.
            //
            // Cross-fading by giving each pose a partial alpha that sums to 1 does NOT preserve
            // opacity: alpha compositing multiplies rather than adds, so mid-blend the plant was
            // genuinely see-through -- the "fades for a bit" after every shot. Keeping one layer solid
            // means total coverage never drops.
            batch.setColor(tint);
            SpritePlacer.drawStanding(batch, sprite, clip, stateTime, cx, fy, true, null);

            String outgoing = ClipMap.firstAvailable(sprite, "attack", "special", "shooting");
            if (!ClipMap.IDLE.equals(outgoing)) {
                batch.setColor(tint.r, tint.g, tint.b, 1f - t);
                SpritePlacer.drawStanding(batch, sprite, outgoing,
                        ClipMap.sample(sprite, outgoing, Float.MAX_VALUE), cx, fy, true, null);
            }
            batch.setColor(previous);
            return;
        }

        batch.setColor(tint);
        // Plants face right, toward the oncoming horde.
        SpritePlacer.drawStanding(batch, sprite, clip, stateTime, cx, fy, true, null);

        batch.setColor(previous);
    }

    private String clipFor(EntitySprite sprite, Plant plant, float delta) {
        // A shot in progress wins: it is the animation the player is waiting to see.
        Float remaining = attacking.get(plant);
        if (remaining != null) {
            float left = remaining - delta;
            if (left > 0f) {
                attacking.put(plant, left);
            } else {
                attacking.remove(plant);
                blending.put(plant, BLEND_SECONDS);   // hand over to the crossfade
            }
            // "attack" for shooters, "special" for sun producers (Sunflower's produce animation).
            String shot = ClipMap.firstAvailable(sprite, "attack", "special", "shooting");
            if (!ClipMap.IDLE.equals(shot)) {
                return shot;
            }
        }

        // Defenders visibly degrade. Wall-nut ships idle / damage / damage2 / damage3 for exactly
        // this, which is the spec's "visual degradation at 2 or 3 health thresholds".
        String damaged = damageClip(sprite, plant);
        if (damaged != null) {
            return damaged;
        }
        return ClipMap.firstAvailable(sprite, ClipMap.IDLE);
    }

    private static String damageClip(EntitySprite sprite, Plant plant) {
        if (plant.getHealth() == null || !sprite.hasClip("damage")) {
            return null;
        }
        int max = Math.max(1, plant.getHealth().getMaxHp());
        float fraction = plant.getHealth().getCurrentHp() / (float) max;
        if (fraction > 0.66f) {
            return null;    // unhurt: fall through to idle
        }
        if (fraction > 0.33f) {
            return "damage";
        }
        return sprite.hasClip("damage2") ? "damage2" : "damage";
    }

    private float footY(int row) {
        return lawn.worldY(row) + lawn.cellHeight() * SpritePlacer.FOOT_INSET;
    }

    private static Color tintFor(Plant plant) {
        if (plant.hasOctopus()) {
            return OCTOPUS_TINT;
        }
        int chill = plant.getChillLevel();
        if (chill > 0) {
            return CHILL_TINT[Math.min(chill, CHILL_TINT.length - 1)];
        }
        return Color.WHITE;
    }
}
