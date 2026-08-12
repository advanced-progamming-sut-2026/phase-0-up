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

    // How long a fed plant keeps its plant-food animation going. Roughly how long the boost itself
    // takes to play out -- a Peashooter's queued burst is several shots over a couple of seconds.
    private static final float PLANT_FOOD_SECONDS = 2.0f;
    private static final float PLANT_FOOD_MAX_SECONDS = 3.5f;

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
    // Plants already seen holding plant food, so the one-way flag fires its animation exactly once.
    private final java.util.Set<Plant> fed =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    // Plants whose currently-playing action clip is the plant-food one.
    private final java.util.Set<Plant> plantFood =
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
                fed.remove(plant);
                plantFood.remove(plant);
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
        float elapsed = phase != null ? phase : freeRunning;
        // Plant food is the one action that repeats: its clip is far shorter than the window it plays
        // across, so the phase is wrapped rather than clamped. ClipMap.sample would otherwise hold the
        // final frame for the rest of the window, which looks like the animation stopped.
        // Only the LOOP stage wraps -- the two bookends play once each, at their own length.
        if (phase != null && plantFood.contains(plant)
                && plantFoodStage.getOrDefault(plant, STAGE_ON) == STAGE_LOOP) {
            float cycle = sprite.clipDuration(clip);
            if (cycle > 0f) {
                elapsed = elapsed % cycle;
            }
        }
        float stateTime = ClipMap.sample(sprite, clip, elapsed);

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
        noteNewActions(plant);

        if (plant.isWindingUp()) {
            if (winding.add(plant)) {
                actionPhase.put(plant, 0f);   // rising edge: a fresh action just began
            }
        } else {
            winding.remove(plant);
        }

        Float phase = actionPhase.get(plant);
        if (phase != null) {
            // Plant food wins while it is playing: "plantfood" for the plants that ship one, otherwise
            // the normal action clip. Peashooter's is a plain "plantfood"; some plants also carry
            // plantfood_on/_off bookends, which are left for T8.6's aura work.
            String action = plantFood.contains(plant)
                    ? plantFoodClip(sprite, plant)
                    // "attack"/"shooting" for shooters, "special" for sun producers (Sunflower's bloom).
                    : ClipMap.firstAvailable(sprite, "attack", "shooting", "special");
            if (!ClipMap.IDLE.equals(action)) {
                float length = sprite.clipDuration(action);
                if (length <= 0f) {
                    length = ATTACK_SECONDS;
                }
                if (plantFood.contains(plant)) {
                    length = plantFoodStageLength(sprite, plant);
                }
                float advanced = phase + delta;
                if (advanced < length) {
                    actionPhase.put(plant, advanced);
                    return action;
                }
                // Plant food is a three-part sequence, so the end of one stage starts the next rather
                // than ending the whole thing.
                if (plantFood.contains(plant) && advancePlantFoodStage(sprite, plant)) {
                    actionPhase.put(plant, 0f);
                    return plantFoodClip(sprite, plant);
                }
            }
            actionPhase.remove(plant);
            plantFood.remove(plant);
            plantFoodStage.remove(plant);
        }

        // A mine that has not finished burying itself is a lump in the dirt, not a live potato with its
        // eyes open. Checked after the action clips so an arming mine still animates if it acts.
        if (!plant.isArmed()) {
            String buried = ClipMap.firstAvailable(sprite, "plant_idle");
            if (!ClipMap.IDLE.equals(buried)) {
                return buried;
            }
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

    // Plant food is a one-off: the flag is set once and never cleared, so its rising edge is the cue.
    // Checked before the wind-up because feeding a plant should visibly interrupt whatever it was doing.
    private void noteNewActions(Plant plant) {
        if (plant.hasPlantFood() && fed.add(plant)) {
            actionPhase.put(plant, 0f);
            plantFood.add(plant);
            // Start at the build-up, or straight at the loop for a plant that has no plantfood_on.
            // Entering at STAGE_ON regardless would substitute the middle clip for the missing build-up
            // and then play it AGAIN as the loop, doubling how long a Peashooter glows.
            EntitySprite sprite = sprites.get(plant.getName());
            plantFoodStage.put(plant, sprite.hasClip("plantfood_on") ? STAGE_ON : STAGE_LOOP);
        }
    }

    // Plant food plays as three parts, the way the art is cut: plantfood_on is the build-up, plantfood
    // is a short cycle that REPEATS while the boost works, and plantfood_off is the wind-down.
    //
    // Not every plant ships all three -- Peashooter has only the middle -- so a missing stage is
    // skipped rather than special-cased. 0 = on, 1 = loop, 2 = off.
    private static final int STAGE_ON = 0;
    private static final int STAGE_LOOP = 1;
    private static final int STAGE_OFF = 2;

    private final Map<Plant, Integer> plantFoodStage = new IdentityHashMap<>();

    private static String stageClip(int stage) {
        return switch (stage) {
            case STAGE_ON -> "plantfood_on";
            case STAGE_OFF -> "plantfood_off";
            default -> "plantfood";
        };
    }

    private String plantFoodClip(EntitySprite sprite, Plant plant) {
        int stage = plantFoodStage.getOrDefault(plant, STAGE_ON);
        String wanted = stageClip(stage);
        if (sprite.hasClip(wanted)) {
            return wanted;
        }
        // Stage missing from this plant's art: fall back to the looping middle, then to the attack clip
        // so a fed plant still does something visible.
        return ClipMap.firstAvailable(sprite, "plantfood", "plantfood2", "attack");
    }

    // How long the current stage lasts. The bookends run for exactly their own length; the middle runs
    // for a fixed window because its clip is a sixth of a second and is meant to repeat.
    private float plantFoodStageLength(EntitySprite sprite, Plant plant) {
        int stage = plantFoodStage.getOrDefault(plant, STAGE_ON);
        if (stage == STAGE_LOOP) {
            // The loop lasts as long as the boost does, between a floor and a ceiling.
            //
            // The floor covers boosts that are instantaneous (a lane freeze) or already over. The
            // ceiling matters because a queued burst is 60 shots an eighth of a second apart -- six
            // seconds of animation -- and against zombies standing on the plant every one of those peas
            // is absorbed the instant it spawns. The shots visibly stop long before the queue does, so
            // an uncapped loop leaves the plant glowing at nothing.
            return plant.isPlantFoodActive() ? PLANT_FOOD_MAX_SECONDS : PLANT_FOOD_SECONDS;
        }
        if (!sprite.hasClip(stageClip(stage))) {
            return PLANT_FOOD_SECONDS;
        }
        float length = sprite.clipDuration(stageClip(stage));
        return length > 0f ? length : ATTACK_SECONDS;
    }

    // Moves to the next stage, returning false once the wind-down has finished.
    private boolean advancePlantFoodStage(EntitySprite sprite, Plant plant) {
        int stage = plantFoodStage.getOrDefault(plant, STAGE_ON);
        if (stage >= STAGE_OFF) {
            return false;
        }
        // A plant with no plantfood_off ends after the loop, so the sequence is however much of it the
        // art actually has.
        if (stage == STAGE_LOOP && !sprite.hasClip("plantfood_off")) {
            return false;
        }
        plantFoodStage.put(plant, stage + 1);
        return true;
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
