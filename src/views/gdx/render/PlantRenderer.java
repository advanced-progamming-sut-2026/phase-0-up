package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import models.entities.plants.Plant;
import models.map.Cell;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.PlantDamage;
import views.gdx.sprite.SpriteRegistry;

import java.util.IdentityHashMap;
import java.util.Map;

// Draws the plants of one lane.
//
// A tile can hold three plants at once and the order they are drawn in is not cosmetic -- it is the
// same order zombies eat through them (Cell.getDefendingPlant): a Lily Pad is the platform underneath,
// the real plant sits on it, and a Pumpkin is the shell in front.
public final class PlantRenderer {

    // Fallback length for an action animation whose real duration the sprite does not report.
    private static final float ATTACK_SECONDS = 0.45f;

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

    // Seconds into its action clip, for each plant currently playing one. Set to 0 the frame the model
    // announces a wind-up, then advanced until the clip runs out.
    private final Map<Plant, Float> actionPhase = new IdentityHashMap<>();
    // Plants whose wind-up was already running last frame, so a new one is detected on its RISING edge
    // rather than restarting the clip every frame the flag stays true.
    private final java.util.Set<Plant> winding =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    // NOTE: two earlier approaches are deliberately gone.
    //
    // The first predicted the next shot from the plant's actionInterval and started the clip early. It
    // fought with a post-shot pulse that selected the same clip, so the animation played TWICE per
    // cycle. The second cross-faded attack over idle to soften the hand-off; blending two copies of
    // skeletal art whose parts overlap reads as a flash, not a dissolve -- that was the "blink".
    //
    // Neither is needed now. The model announces the wind-up, so one clip plays once, straight
    // through, and the join back to idle happens at the pose the clip ends on.

    public PlantRenderer(SpriteRegistry sprites, LawnGeometry lawn, AnimationClocks clocks) {
        this.sprites = sprites;
        this.lawn = lawn;
        this.clocks = clocks;
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
            if (plant != null) {
                // A plant is still drawn on the tick it dies, so this is where its per-plant state is
                // dropped. Without it a long level accumulates an entry per plant that ever died.
                actionPhase.remove(plant);
                winding.remove(plant);
            }
            return;
        }
        EntitySprite sprite = sprites.get(plant.getName());
        int damageStage = PlantDamage.stageFor(plant,
                PlantDamage.stageCount(sprite, plant.getName()));
        String clip = clipFor(sprite, plant, delta, damageStage);

        // The clock is advanced even while an action clip is driving the pose, so the plant keeps its
        // entry in AnimationClocks: dropping out of the map and back in would reset idle to frame 0
        // after every shot, which is its own visible jump.
        float freeRunning = clocks.advance(plant, clip, delta);
        Float phase = actionPhase.get(plant);
        float stateTime = ClipMap.sample(sprite, clip, phase != null ? phase : freeRunning);

        Color previous = batch.getColor().cpy();
        Color tint = tintFor(plant);
        float cx = lawn.centerX(col);
        float fy = footY(row);

        batch.setColor(tint);
        // Plants face right, toward the oncoming horde. The visibility map is what actually cracks a
        // Wall-nut's shell -- the damage clips only change its face.
        SpritePlacer.drawStanding(batch, sprite, clip, stateTime, cx, fy, true,
                PlantDamage.visibilityFor(sprite, plant.getName(), damageStage));

        batch.setColor(previous);
    }

    private String clipFor(EntitySprite sprite, Plant plant, float delta, int damageStage) {
        // An action clip plays ONCE, WHOLE: wind-up, release, follow-through, then back to idle.
        //
        // The model announces the wind-up and holds the effect back until it ends, so the release
        // frame is when the pea appears / the sun pops out. What makes the return to idle smooth is
        // the part AFTER that: the clip is allowed to run to its own end. Cutting to idle on the
        // release -- which is what happened while the clip was gated on isWindingUp() alone -- drops
        // the plant from a mid-lunge pose straight into the rest pose, and that discontinuity is the
        // jerk. Played out, the clip settles back to rest by itself, so the switch lands on two poses
        // that already match and there is nothing to see.
        if (plant.isWindingUp()) {
            if (winding.add(plant)) {
                actionPhase.put(plant, 0f);   // rising edge: a fresh action just began
            }
        } else {
            winding.remove(plant);
        }

        Float phase = actionPhase.get(plant);
        if (phase != null) {
            // "attack"/"shooting" for shooters, "special" for sun producers (Sunflower's bloom).
            String action = ClipMap.firstAvailable(sprite, "attack", "shooting", "special");
            if (!ClipMap.IDLE.equals(action)) {
                float length = sprite.clipDuration(action);
                if (length <= 0f) {
                    length = ATTACK_SECONDS;
                }
                float advanced = phase + delta;
                if (advanced < length) {
                    actionPhase.put(plant, advanced);
                    return action;
                }
            }
            actionPhase.remove(plant);
        }

        // Defenders visibly degrade -- the spec's "visual degradation at 2 or 3 health thresholds".
        // The stage was decided in draw(), so the clip and the part swap can never disagree about how
        // hurt the plant is.
        String damaged = PlantDamage.clipFor(sprite, damageStage);
        if (damaged != null) {
            return damaged;
        }
        return ClipMap.firstAvailable(sprite, ClipMap.IDLE);
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
