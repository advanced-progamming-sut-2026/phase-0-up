package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import models.entities.plants.Plant;
import models.map.Cell;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.PlantDamage;
import views.gdx.sprite.PlantStages;
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

    // How long a fed plant keeps its plant-food animation going is NOT a number here any more.
    //
    // It used to be: a two-second window with a three-and-a-half second ceiling, both guesses. A Snow
    // Pea's boost is sixty shots one tick apart, which is six seconds at the model's fixed 10 Hz, so
    // the plant stopped glowing while it was still visibly firing -- and a plant whose boost was over
    // in an instant glowed for two seconds at nothing.
    //
    // Plant.isPlantFoodActive() is now the whole answer: the loop stage replays for exactly as long as
    // it says the boost is running, then hands over to the wind-down. That holds for every plant
    // without a table of per-plant durations, and it cannot drift when a plant's shot count is retuned.

    // Chill, in the three stages the model actually tracks -- and the game ships art for all three,
    // none of which was being used.
    //
    //   chill 1   FROSTBITE_CHILL_PLANT / chill_stage1   frost creeping over the plant
    //   chill 2   FROSTBITE_CHILL_PLANT / chill_stage2   more of it, and higher up
    //   chill 3   FROSTBITE_ICE_BLOCK_PLANT              frozen solid, inside the two-part block
    //
    // The tint below stays underneath all three. It is what makes the PLANT look cold rather than the
    // frost look stuck on, but it was never enough on its own: three stages expressed only as three
    // shades of blue read as "that plant is slightly bluer than it was", which is the report this came
    // from.
    //
    // Stage 3 borrows the same block TerrainRenderer draws for an authored '&' obstacle, in the same
    // two passes -- the rear half behind the plant, the front half over it at partial alpha -- so a
    // plant frozen by chill and a plant caged by the level look like the same thing, because they are.
    // Nothing drew it before: Plant.freezePlant only sets a flag and adds no FrozenTerrain to the cell,
    // so the block existed in the rules and nowhere on screen.
    private static final String CHILL_SPRITE = "FROSTBITE_CHILL_PLANT";
    private static final String[] CHILL_STAGE_CLIPS = {"chill_stage1", "chill_stage2"};
    private static final String ICE_BLOCK_FRONT = "FROSTBITE_ICE_BLOCK_PLANT";
    private static final String ICE_BLOCK_BEHIND = "FROSTBITE_ICE_BLOCK_PLANT_BEHIND";
    private static final String[] ICE_BLOCK_FRONT_CLIPS = {"freeze_idle", "idle"};
    private static final String[] ICE_BLOCK_REAR_CLIPS = {"idle"};

    // How much of the plant still reads through the front half of the block. The same 0.45 the terrain
    // blocks use, and for the same reason: Frostbite is painted in near-white ice and the block art is
    // near-white too.
    private static final float ICE_FRONT_ALPHA = 0.45f;

    // The tint under all of it. Three steps, matching Plant.getChillLevel()'s 1..3.
    private static final Color[] CHILL_TINT = {
            Color.WHITE,
            new Color(0.80f, 0.92f, 1f, 1f),
            new Color(0.62f, 0.84f, 1f, 1f),
            new Color(0.45f, 0.76f, 1f, 1f),
    };

    // A snared plant used to get a flat purple tint and nothing else, which read as poisoned rather
    // than as grabbed -- and left T8.4 with no octopus to flash, because there was no octopus. The dump
    // ships one, so it is drawn instead: an orange octopus clinging to the plant, over the top of it.
    //
    // `animation3` is the 3s clinging loop; `animation` is the 0.3s throw and `die` the 2s release,
    // neither of which is the state a snared plant is in.
    private static final String OCTOPUS_SPRITE = "ZOMBIE_OCTOPUS_PROJECTILE";
    private static final String[] OCTOPUS_CLIPS = {"animation3", "animation4", "animation2"};

    // Drawn a shade wider than the plant it has hold of, so the tentacles read as wrapping round it
    // rather than sitting on it, and lifted to the plant's middle rather than its feet.
    private static final float OCTOPUS_WIDTH_CELLS = 0.95f;
    private static final float OCTOPUS_LIFT_CELLS = 0.42f;

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final AnimationClocks clocks;

    // Watches health frame to frame; a drop is a hit. See DamageFlash.
    private final DamageFlash flashes = new DamageFlash();

    // Seconds into its action clip, for each plant currently playing one. Set to 0 the frame the model
    // announces a wind-up, then advanced until the clip runs out.
    private final Map<Plant, Float> actionPhase = new IdentityHashMap<>();
    // Plants whose wind-up was already running last frame, so a new one is detected on its RISING edge
    // rather than restarting the clip every frame the flag stays true.
    private final java.util.Set<Plant> winding =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    // How many feeds each plant had been given when its animation last started, so a SECOND feed
    // restarts the sequence instead of being swallowed.
    private final Map<Plant, Integer> fed = new IdentityHashMap<>();
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
                plantFoodStage.remove(plant);
                lastStage.remove(plant);
                growing.remove(plant);
                idlePhase.remove(plant);
                idleVariant.remove(plant);
                lastStrike.remove(plant);
                frostKeys.remove(plant);
                glowPhase.remove(plant);
                glowStopped.remove(plant);
                reportedStage.remove(plant);
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
        // No wrapping here any more. The loop stage used to run for a fixed window far longer than its
        // own clip and wrap the phase to fake the repeat; it now restarts the stage each time the clip
        // ends (see actionClip), so the phase never runs past one cycle in the first place.
        float stateTime = ClipMap.sample(sprite, clip, elapsed);

        Color previous = batch.getColor().cpy();
        Color tint = tintFor(plant);
        float cx = lawn.centerX(col);
        float fy = footY(row);

        // Rear half of the ice block first, so a frozen plant sits INSIDE it. Drawn before the tint is
        // applied to the plant, and at its own colour.
        drawIceBehind(batch, plant, cx, fy, delta);

        // The plant-food aura, behind the plant so it haloes rather than covers.
        float glow = advancePlantFoodGlow(plant, delta);
        drawPlantFoodAura(batch, plant, cx, fy, glow);

        batch.setColor(tint);
        // Plants face right, toward the oncoming horde. The visibility map is what actually cracks a
        // Wall-nut's shell -- the damage clips only change its face.
        java.util.Map<String, Boolean> parts =
                PlantDamage.visibilityFor(sprite, plant.getName(), damageStage);
        SpritePlacer.drawStanding(batch, sprite, clip, stateTime, cx, fy, true, parts);

        drawHitFlash(batch, sprite, plant, clip, stateTime, cx, fy, parts, delta);
        drawPlantFoodShine(batch, sprite, plant, clip, stateTime, cx, fy, parts, glow);

        if (plant.hasOctopus()) {
            drawOctopus(batch, plant, cx, fy, delta);
        }

        // Frost over the top: stage 1 and 2 creeping over the plant, or the front half of the block.
        drawFrostOver(batch, plant, cx, fy, delta);
        flashIceBlock(batch, plant, cx, fy, delta);

        batch.setColor(previous);
    }


    // ---- plant food -----------------------------------------------------------------------------

    // The aura the game ships for exactly this: EFFECTS/PLANTFOOD_FX, cut the same three ways a
    // plant's own plant-food art is (a 0.23s build-up, a 2.5s cycle, a 0.27s wind-down). It was in the
    // dump the whole time and nothing drew it, so a fed plant was only distinguishable from an unfed
    // one by its pose -- which for the several plants that ship no plantfood clip at all meant not at
    // all.
    private static final String GLOW_SPRITE = "PLANTFOOD_FX";
    private static final String GLOW_ON = "plantfood_on";
    private static final String GLOW_LOOP = "plantfood";
    private static final String GLOW_OFF = "plantfood_off";

    // Seconds the aura spends coming up and going away. Both are envelopes on alpha, not clip lengths:
    // the art pops in hard, and a fed plant appearing to switch on with a click is the thing they fix.
    private static final float GLOW_RISE = 0.22f;
    private static final float GLOW_FALL = 0.32f;

    // How bright the aura gets, and how strongly the plant ITSELF lights up underneath it. The second
    // is what the aura alone could not do: the halo sits behind the plant, so without this the plant is
    // a dark shape in front of a glow rather than a glowing plant.
    private static final float GLOW_AURA_ALPHA = 0.85f;
    private static final float SHINE_MIN = 0.10f;
    private static final float SHINE_MAX = 0.30f;
    private static final float SHINE_HZ = 2.6f;

    // Not white: an additive white wash just blows the plant out flat. The green is the plant-food
    // green, so the plant reads as lit BY the food rather than overexposed.
    private static final Color SHINE = new Color(0.42f, 1f, 0.36f, 1f);

    // Seconds since this plant's aura began, and the reading of that same clock at the moment the model
    // stopped calling the boost active -- which is where the fade-out starts from.
    private final Map<Plant, Float> glowPhase = new IdentityHashMap<>();
    private final Map<Plant, Float> glowStopped = new IdentityHashMap<>();

    // Advances the aura clock and returns its strength, 0 when this plant is not glowing at all.
    //
    // Driven off the SAME window as the plant-food animation (plantFood), so the halo and the pose can
    // never disagree about whether the plant is boosted. Its fade-out is driven off the model instead,
    // so the light starts dying the moment the effect does rather than waiting for the wind-down clip.
    private float advancePlantFoodGlow(Plant plant, float delta) {
        if (!plantFood.contains(plant)) {
            reportFeedEnd(plant);
            glowPhase.remove(plant);
            glowStopped.remove(plant);
            return 0f;
        }
        float phase = glowPhase.getOrDefault(plant, 0f) + delta;
        glowPhase.put(plant, phase);

        if (plant.isPlantFoodActive()) {
            glowStopped.remove(plant);
        } else {
            glowStopped.putIfAbsent(plant, phase);
        }
        reportFeedStage(plant, phase);

        float strength = Math.min(1f, phase / GLOW_RISE);
        Float stopped = glowStopped.get(plant);
        if (stopped != null) {
            strength = Math.min(strength, Math.max(0f, 1f - (phase - stopped) / GLOW_FALL));
        }
        return strength;
    }

    // ---- -Dpvz.feedCheck ------------------------------------------------------------------------

    // The stage each fed plant was last seen in, so a change is logged once rather than every frame.
    private final Map<Plant, Integer> reportedStage = new IdentityHashMap<>();

    private void reportFeedStage(Plant plant, float phase) {
        if (!views.gdx.core.DebugFlags.FEED_CHECK) {
            return;
        }
        int stage = plantFoodStage.getOrDefault(plant, STAGE_ON);
        Integer seen = reportedStage.put(plant, stage);
        if (seen != null && seen == stage) {
            return;
        }
        com.badlogic.gdx.Gdx.app.log("FeedCheck", String.format(
                "%s %s at %.2fs  boostRunning=%s",
                plant.getName(), stageClip(stage), phase, plant.isPlantFoodActive()));
    }

    private void reportFeedEnd(Plant plant) {
        if (!views.gdx.core.DebugFlags.FEED_CHECK) {
            return;
        }
        reportedStage.remove(plant);
        Float drawn = glowPhase.get(plant);
        if (drawn == null) {
            return;
        }
        Float stopped = glowStopped.get(plant);
        com.badlogic.gdx.Gdx.app.log("FeedCheck", String.format(
                "%s DONE  boost ran %s,  drawn boosted %.2fs",
                plant.getName(),
                stopped == null ? "past the animation" : String.format("%.2fs", stopped),
                drawn));
    }

    // Which part of the aura's own sequence to show. Its build-up runs on the aura's clock rather than
    // on the plant's stage, because a plant with no plantfood_on of its own starts at the loop and the
    // aura would then have no build-up either.
    private String glowClip(EntitySprite glowSprite, Plant plant, float phase) {
        if (plantFoodStage.getOrDefault(plant, STAGE_ON) >= STAGE_OFF && glowSprite.hasClip(GLOW_OFF)) {
            return GLOW_OFF;
        }
        float onLength = glowSprite.clipDuration(GLOW_ON);
        if (onLength > 0f && phase < onLength && glowSprite.hasClip(GLOW_ON)) {
            return GLOW_ON;
        }
        return GLOW_LOOP;
    }

    private void drawPlantFoodAura(Batch batch, Plant plant, float cx, float fy, float strength) {
        if (strength <= 0f) {
            return;
        }
        EntitySprite glowSprite = sprites.get(GLOW_SPRITE);
        if (glowSprite == null || !glowSprite.isReady()) {
            return;   // the shine below still lights the plant up
        }
        float phase = glowPhase.getOrDefault(plant, 0f);
        String clip = glowClip(glowSprite, plant, phase);

        // Wrapped by hand. "plantfood" is one of ClipMap's one-shot prefixes -- correctly, for a
        // plant's own pose -- so sample() would hold the aura's final frame for the rest of a long
        // boost, which is a glow that visibly stops moving while the plant keeps firing.
        float length = glowSprite.clipDuration(clip);
        float elapsed = (GLOW_LOOP.equals(clip) && length > 0f) ? phase % length : phase;

        float previous = batch.getPackedColor();
        SpritePlacer.beginAdditive(batch);
        batch.setColor(1f, 1f, 1f, GLOW_AURA_ALPHA * strength);
        SpritePlacer.drawStanding(batch, glowSprite, clip, ClipMap.sample(glowSprite, clip, elapsed),
                cx, fy, true, null);
        SpritePlacer.endAdditive(batch);
        batch.setPackedColor(previous);
    }

    // The plant's own frame drawn again in green, additively -- the same trick as the hit flash, but
    // pulsing and held for the whole boost rather than decaying over a few frames. Uses the frame that
    // was just drawn, so it lights the plant in whatever pose it is actually in.
    private void drawPlantFoodShine(Batch batch, EntitySprite sprite, Plant plant, String clip,
                                    float stateTime, float cx, float fy,
                                    java.util.Map<String, Boolean> parts, float strength) {
        if (strength <= 0f) {
            return;
        }
        float pulse = 0.5f + 0.5f * (float) Math.sin(glowPhase.getOrDefault(plant, 0f)
                * SHINE_HZ * (float) Math.PI * 2f);
        float amount = (SHINE_MIN + (SHINE_MAX - SHINE_MIN) * pulse) * strength;

        float previous = batch.getPackedColor();
        SpritePlacer.beginAdditive(batch);
        batch.setColor(SHINE.r * amount, SHINE.g * amount, SHINE.b * amount, 1f);
        SpritePlacer.drawStanding(batch, sprite, clip, stateTime, cx, fy, true, parts);
        SpritePlacer.endAdditive(batch);
        batch.setPackedColor(previous);
    }

    // ---- chill and ice --------------------------------------------------------------------------

    // The plant's own ice block lighting up when it is shot.
    //
    // A separate HP pool from the terrain blocks TerrainRenderer flashes, and a separate flash instance
    // from the plant's own: Projectile sends its damage into Plant.damageIceBlock while the plant
    // underneath is untouched, so flashing the plant would credit the hit to the wrong thing -- the same
    // split the octopus needed, for the same reason.
    private void flashIceBlock(Batch batch, Plant plant, float cx, float fy, float delta) {
        if (!drawnAsFrozen(plant) || plant.getIceBlockHp() <= 0) {
            return;
        }
        float flash = iceFlashes.intensity(plant, plant.getIceBlockHp(), delta);
        if (flash <= 0f) {
            return;
        }
        EntitySprite sprite = sprites.get(ICE_BLOCK_FRONT);
        if (sprite == null || !sprite.isReady()) {
            return;
        }
        String clip = ClipMap.firstAvailable(sprite, ICE_BLOCK_FRONT_CLIPS);
        float stateTime = ClipMap.sample(sprite, clip,
                clocks.advance(frostKey(ICE_BLOCK_FRONT, plant), clip, 0f));

        float previous = batch.getPackedColor();
        SpritePlacer.beginAdditive(batch);
        batch.setColor(flash, flash, flash, 1f);
        SpritePlacer.drawStanding(batch, sprite, clip, stateTime, cx, fy, true, null);
        SpritePlacer.endAdditive(batch);
        batch.setPackedColor(previous);
    }

    private final DamageFlash iceFlashes = new DamageFlash();

    // The chill stage to DRAW. Normally the plant's own, but -Dpvz.forceChill pins it so all three
    // stages can be put on screen at once -- see DebugFlags.FORCE_CHILL for why that is needed.
    private static int chillStageOf(Plant plant) {
        int forced = views.gdx.core.DebugFlags.FORCE_CHILL;
        return forced >= 0 ? forced : plant.getChillLevel();
    }

    private static boolean drawnAsFrozen(Plant plant) {
        return chillStageOf(plant) >= 3;
    }

    // The rear half of the block, drawn BEFORE the plant so the plant is inside the ice rather than
    // behind a sticker. Same two-pass trick TerrainRenderer uses for an authored '&'.
    private void drawIceBehind(Batch batch, Plant plant, float cx, float fy, float delta) {
        if (!drawnAsFrozen(plant)) {
            return;
        }
        drawFrostAt(batch, plant, ICE_BLOCK_BEHIND, ICE_BLOCK_REAR_CLIPS, cx, fy, delta, 1f);
    }

    // Whatever goes OVER the plant: the frost of stages 1 and 2, or the front half of the block at 3.
    private void drawFrostOver(Batch batch, Plant plant, float cx, float fy, float delta) {
        if (drawnAsFrozen(plant)) {
            drawFrostAt(batch, plant, ICE_BLOCK_FRONT, ICE_BLOCK_FRONT_CLIPS, cx, fy, delta,
                    ICE_FRONT_ALPHA);
            return;
        }
        int chill = chillStageOf(plant);
        if (chill <= 0) {
            return;
        }
        // Clamped rather than indexed blindly: getChillLevel returns 3 only when isFrozen, which is
        // handled above, but a retune of the freeze threshold must not walk off the end of this array.
        String clip = CHILL_STAGE_CLIPS[Math.min(chill, CHILL_STAGE_CLIPS.length) - 1];
        drawFrostAt(batch, plant, CHILL_SPRITE, new String[] {clip}, cx, fy, delta, 1f);
    }

    // Both halves come through here. Drawn at the plant's own foot line and at the art's authored size,
    // exactly as TerrainRenderer draws a terrain block, so a chill-frozen plant and a caged one line up.
    //
    // Clocked on a key of this renderer's own rather than on the plant: the plant's clock is already
    // tracking its body clip, and AnimationClocks resets a clock whenever its clip changes, so sharing
    // the key would restart the plant's animation every frame.
    private void drawFrostAt(Batch batch, Plant plant, String spriteName, String[] preferredClips,
                             float cx, float fy, float delta, float alpha) {
        EntitySprite sprite = sprites.get(spriteName);
        if (sprite == null || !sprite.isReady()) {
            return;   // the tint has already been applied; the plant still reads as cold
        }
        String clip = ClipMap.firstAvailable(sprite, preferredClips);
        float stateTime = ClipMap.sample(sprite, clip,
                clocks.advance(frostKey(spriteName, plant), clip, delta));

        float previous = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, alpha);
        SpritePlacer.drawStanding(batch, sprite, clip, stateTime, cx, fy, true, null);
        batch.setPackedColor(previous);
    }

    // One stable key per (art, plant) pair. Interned for the same reason TerrainRenderer interns its
    // tile keys: AnimationClocks is keyed by identity, so a string built fresh each frame is a new
    // clock each frame and the frost never animates.
    private final java.util.Map<Plant, java.util.Map<String, String>> frostKeys =
            new java.util.IdentityHashMap<>();

    private Object frostKey(String spriteName, Plant plant) {
        return frostKeys.computeIfAbsent(plant, p -> new java.util.HashMap<>())
                .computeIfAbsent(spriteName, name -> name + "#"
                        + Integer.toHexString(System.identityHashCode(plant)));
    }
    // Hit flash: the same frame drawn again, additively, so the plant lights up white. Drawn after the
    // sprite rather than instead of it, so the art stays readable underneath.
    private void drawHitFlash(Batch batch, EntitySprite sprite, Plant plant, String clip,
                              float stateTime, float cx, float fy,
                              java.util.Map<String, Boolean> parts, float delta) {
        float flash = plant.getHealth() == null ? 0f
                : flashes.intensity(plant, plant.getHealth().getCurrentHp(), delta);
        if (flash <= 0f) {
            return;
        }
        SpritePlacer.beginAdditive(batch);
        batch.setColor(flash, flash, flash, 1f);
        SpritePlacer.drawStanding(batch, sprite, clip, stateTime, cx, fy, true, parts);
        SpritePlacer.endAdditive(batch);
    }

    // The octopus clinging to a snared plant, and its own hit flash (T8.4).
    //
    // It gets a flash of its own rather than borrowing the plant's, because they take damage
    // SEPARATELY: Projectile.onHit sends a shot into damageOctopus while the plant underneath is
    // untouched, so flashing the plant would credit the hit to the wrong thing -- and the plant, being
    // undamaged, would never flash at all while the player shot the octopus off it.
    private void drawOctopus(Batch batch, Plant plant, float cx, float footY, float delta) {
        EntitySprite octopus = sprites.get(OCTOPUS_SPRITE);
        if (octopus == null || !octopus.isReady()) {
            return;
        }
        String clip = ClipMap.firstAvailable(octopus, OCTOPUS_CLIPS);
        // One shared clock, advanced once a frame in sweepFlashes, plus a per-plant phase derived from
        // the tile it is on. AnimationClocks is an IdentityHashMap, so a per-octopus key would have to
        // be an object that survives between frames -- and a stable offset off the coordinates gives the
        // same "they do not writhe in unison" result with no state at all.
        float phase = (float) (plant.getX() * 0.37 + plant.getY() * 0.61);
        float stateTime = ClipMap.sample(octopus, clip, octopusClock + phase);
        float centreY = footY + lawn.cellHeight() * OCTOPUS_LIFT_CELLS;

        com.badlogic.gdx.math.Rectangle bounds = octopus.bounds(clip);
        if (bounds == null || bounds.width <= 0f) {
            return;
        }
        float scale = SpritePlacer.toSpriteSpace(OCTOPUS_WIDTH_CELLS * lawn.cellWidth())
                / bounds.width;

        float previous = batch.getPackedColor();
        batch.setColor(Color.WHITE);
        drawOctopusAt(batch, octopus, clip, stateTime, cx, centreY, scale, bounds);

        // Keyed on the PLANT, in a DamageFlash of its own. The plant is the only stable identity the
        // octopus has -- it is not an entity in the model, just two fields on the plant it grabbed --
        // and a second instance is what keeps its hits from being confused with the plant's own.
        float flash = octopusFlashes.intensity(plant, plant.getOctopusHp(), delta);
        if (flash > 0f) {
            SpritePlacer.beginAdditive(batch);
            batch.setColor(flash, flash, flash, 1f);
            drawOctopusAt(batch, octopus, clip, stateTime, cx, centreY, scale, bounds);
            SpritePlacer.endAdditive(batch);
        }
        batch.setPackedColor(previous);
    }

    private void drawOctopusAt(Batch batch, EntitySprite octopus, String clip, float stateTime,
                               float cx, float centreY, float scale,
                               com.badlogic.gdx.math.Rectangle bounds) {
        octopusTransform.begin(batch, SpritePlacer.toSpriteSpace(cx),
                SpritePlacer.toSpriteSpace(centreY), scale);
        octopus.draw(batch, clip, stateTime, 0f, bounds.y + bounds.height / 2f, true);
        octopusTransform.end(batch);
    }

    private final LocalTransform octopusTransform = new LocalTransform();
    private final DamageFlash octopusFlashes = new DamageFlash();
    private float octopusClock;

    // Called once per frame by GameRenderer: drops plants that were not drawn, and advances the one
    // clock the octopuses share. Advancing it in draw() would run it once per snared plant per frame.
    void sweepFlashes(float delta) {
        flashes.sweep();
        octopusFlashes.sweep();
        iceFlashes.sweep();
        octopusClock += delta;
    }

    // ---- strikes -------------------------------------------------------------------------------
    //
    // Caulipower and Electric Blueberry hit a zombie anywhere on the board with nothing in flight, and
    // Grave Buster destroys the grave beneath itself. All three ship their own effect animation, and
    // none of them has a Projectile the effect could ride. The model counts its strikes (see Striking);
    // this watches the counter and sends the art from the plant to what it hit.
    private static final java.util.Map<String, String> STRIKE_SPRITE = java.util.Map.of(
            "caulipower", "CAULIPOWER_PROJECTILE",
            "electric blueberry", "ELECTRICBLUEBERRY_CLOUD_PROJECTILE",
            "grave buster", "GRAVEBUSTER_DIRT");

    public record Strike(float fromX, float fromY, float toX, float toY, String sprite) { }

    private final Map<Plant, Integer> lastStrike = new IdentityHashMap<>();
    private final java.util.List<Strike> pendingStrikes = new java.util.ArrayList<>();

    private void noteStrike(Plant plant) {
        String sprite = plant.getName() == null ? null
                : STRIKE_SPRITE.get(plant.getName().toLowerCase(java.util.Locale.ROOT));
        if (sprite == null) {
            return;
        }
        int count = plant.getStrikeCount();
        Integer previous = lastStrike.put(plant, count);
        if (previous == null || count <= previous) {
            return;
        }
        // From the plant's mouth to the zombie it aimed at.
        //
        // This only reads right because the ability now HOLDS its effect for the length of the flight
        // (GlobalTargetingAbility.WIND_UP_TICKS). Without that the damage landed on the tick the shot
        // was fired, so a zombie taking 5000 from an Electric Blueberry was dead before its cloud left
        // the plant -- the cloud then crossed the board to an empty tile, and what the player saw was
        // a zombie dying for no reason followed by unrelated weather. The two are in step now.
        //
        // Grave Buster passes through here too, with both ends on its own tile: its dirt flies nowhere.
        float fromX = lawn.worldX((float) plant.getX());
        float fromY = lawn.worldY(plant.getY()) + lawn.cellHeight() * STRIKE_HEIGHT;
        float toX = lawn.worldX((float) plant.getStrikeX());
        float toY = lawn.worldY((int) Math.round(plant.getStrikeY()))
                + lawn.cellHeight() * STRIKE_HEIGHT;
        pendingStrikes.add(new Strike(fromX, fromY, toX, toY, sprite));
    }

    private static final float STRIKE_HEIGHT = 0.62f;

    // Handed to ImpactEffects once per frame and cleared, for the same reason the muzzle flashes are:
    // this renderer runs per lane, and an effect drawn here would be covered by the next lane.
    public java.util.List<Strike> drainStrikes() {
        if (pendingStrikes.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<Strike> out = java.util.List.copyOf(pendingStrikes);
        pendingStrikes.clear();
        return out;
    }

    // ---- growth stages -------------------------------------------------------------------------
    //
    // Four plants are animated one clip set per growth stage, and two of them (Sun-shroom, Puff-shroom)
    // have NO un-staged clips at all -- so every lookup below goes through PlantStages rather than
    // asking for a bare name. See that class for what the dump actually ships.

    // The stage each plant was last seen in, so a stage-up is caught on its rising edge and its growth
    // clip played exactly once.
    private final Map<Plant, Integer> lastStage = new IdentityHashMap<>();
    // Plants currently playing a growth clip, which is a one-shot action like an attack.
    private final java.util.Set<Plant> growing =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    // Most plants ship two or more resting clips and the real game cycles them -- it is what makes a
    // plant blink rather than sway forever on one loop. Which clips those are is PlantStages' problem;
    // this only remembers where in the cycle each plant is.
    private final Map<Plant, Float> idlePhase = new IdentityHashMap<>();
    private final Map<Plant, Integer> idleVariant = new IdentityHashMap<>();

    // A stage-up is announced by the model changing Plant.getGrowthStage(); the view notices and plays
    // the transition. Nothing tells it to -- there is no event for this -- so it is watched, the same
    // way DamageFlash watches health.
    private void noteGrowth(EntitySprite sprite, Plant plant) {
        int stage = plant.getGrowthStage();
        Integer previous = lastStage.put(plant, stage);
        if (previous == null || stage <= previous) {
            return;
        }
        String growth = PlantStages.growthClip(sprite, previous);
        if (growth == null) {
            return;   // the last stage has no growth clip, and most plants have none at all
        }
        actionPhase.put(plant, 0f);
        growing.add(plant);
        // A growth that interrupts plant food would otherwise leave the plant-food sequence half-played
        // and stuck, because its stage counter is never advanced past the interruption.
        plantFood.remove(plant);
        plantFoodStage.remove(plant);
    }

    // The resting clip, stepping to the next variant each time one finishes.
    //
    // Its own phase rather than AnimationClocks': that clock RESETS whenever the clip name changes, so
    // the very act of cycling would restart the timer that decides when to cycle.
    private String idleClip(EntitySprite sprite, Plant plant, float delta, int stage) {
        java.util.List<String> variants =
                PlantStages.idleVariants(sprite, stage, plant.getLevel());
        if (variants.size() < 2) {
            idlePhase.remove(plant);
            idleVariant.remove(plant);
            return variants.get(0);
        }
        int index = idleVariant.getOrDefault(plant, 0) % variants.size();
        String current = variants.get(index);
        float phase = idlePhase.getOrDefault(plant, 0f) + delta;
        float cycle = sprite.clipDuration(current);
        if (cycle > 0f && phase >= cycle) {
            phase = 0f;
            index = (index + 1) % variants.size();
        }
        idlePhase.put(plant, phase);
        idleVariant.put(plant, index);
        return current;
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
        int stage = plant.getGrowthStage();
        noteNewActions(plant);
        noteGrowth(sprite, plant);
        noteStrike(plant);

        if (plant.isWindingUp()) {
            if (winding.add(plant)) {
                actionPhase.put(plant, 0f);   // rising edge: a fresh action just began
            }
        } else {
            winding.remove(plant);
        }

        Float phase = actionPhase.get(plant);
        if (phase != null) {
            String action = actionClip(sprite, plant, delta, stage, phase);
            if (action != null) {
                return action;
            }
        }

        // A mine that has not finished burying itself is a lump in the dirt, not a live potato with its
        // eyes open. Checked after the action clips so an arming mine still animates if it acts.
        if (!plant.isArmed()) {
            String buried = PlantStages.clip(sprite, stage, "plant_idle");
            if (buried != null) {
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
        return idleClip(sprite, plant, delta, stage);
    }

    // The one-shot clip this plant is in the middle of, advanced by delta -- or null once it has run
    // out, at which point every trace of it is dropped and the caller falls through to idle.
    private String actionClip(EntitySprite sprite, Plant plant, float delta, int stage, float phase) {
        // Growing outranks everything: it is a one-shot transition between two sizes, and cutting away
        // from it mid-way would leave the plant snapping from one to the other.
        //
        // Plant food comes next: "plantfood" for the plants that ship one, otherwise the normal action
        // clip. Peashooter's is a plain "plantfood"; some plants also carry plantfood_on/_off bookends,
        // which are left for T8.6's aura work.
        String action;
        if (growing.contains(plant)) {
            action = PlantStages.growthClip(sprite, stage - 1);
        } else if (plantFood.contains(plant)) {
            action = plantFoodClip(sprite, plant, stage);
        } else {
            // "attack"/"shooting" for shooters, "special" for sun producers (Sunflower's bloom).
            //
            // A plant whose action has more than one form asks for the numbered clip first: Kernel-pult
            // lobs a kernel or, on a roll, stunning butter, and the art has a separate swing for each.
            // Only the ability knows which it threw, so the model reports it (see VariantAction).
            int variant = plant.getActionVariant();
            action = variant > 0
                    ? PlantStages.clip(sprite, stage, "attack" + (variant + 1), "attack", "shooting")
                    : PlantStages.clip(sprite, stage, "attack", "shooting", "special");
        }
        if (action != null) {
            // The clip's own length, always -- including the plant-food stages, which used to be given
            // an invented one. A stage that runs for exactly its clip is a stage that ends on the pose
            // it was drawn to end on.
            float length = sprite.clipDuration(action);
            if (length <= 0f) {
                length = ATTACK_SECONDS;
            }
            float advanced = phase + delta;
            if (advanced < length) {
                actionPhase.put(plant, advanced);
                return action;
            }
            // Plant food is a three-part sequence, so the end of one stage starts the next rather than
            // ending the whole thing -- and the middle stage repeats itself instead of moving on for
            // as long as the model says the boost is still running.
            if (plantFood.contains(plant) && advancePlantFoodStage(sprite, plant, stage)) {
                actionPhase.put(plant, 0f);
                return plantFoodClip(sprite, plant, stage);
            }
        }
        actionPhase.remove(plant);
        plantFood.remove(plant);
        plantFoodStage.remove(plant);
        growing.remove(plant);
        return null;
    }

    // A feed is announced by the model's feed COUNT going up, not by a flag turning true: the flag is
    // set once and never cleared, so it can only ever describe the first feed.
    // Checked before the wind-up because feeding a plant should visibly interrupt whatever it was doing.
    private void noteNewActions(Plant plant) {
        int feeds = plant.getPlantFoodFeeds();
        if (feeds > 0 && fed.getOrDefault(plant, 0) < feeds) {
            fed.put(plant, feeds);
            actionPhase.put(plant, 0f);
            plantFood.add(plant);
            glowPhase.remove(plant);      // a fresh feed gets a fresh aura, from its build-up
            glowStopped.remove(plant);
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

    // growthStage is the plant's SIZE, plantFoodStage is where it is in the on/loop/off sequence. Two
    // different stages with the same word in the name, unavoidably: the art uses "stage" for the first
    // and this class has always used it for the second.
    private String plantFoodClip(EntitySprite sprite, Plant plant, int growthStage) {
        String wanted = stageClip(plantFoodStage.getOrDefault(plant, STAGE_ON));
        String staged = PlantStages.clip(sprite, growthStage, wanted);
        if (staged != null) {
            return staged;
        }
        // Stage missing from this plant's art: fall back to the looping middle, then to the attack clip
        // so a fed plant still does something visible.
        String fallback = PlantStages.clip(sprite, growthStage, "plantfood", "plantfood2", "attack");
        return fallback == null ? ClipMap.IDLE : fallback;
    }

    // Moves the sequence on, returning false once it has finished. Called when the current stage's clip
    // has just run out.
    //
    // The middle stage does not move on while the boost is still running -- it stays where it is and
    // the caller replays it from frame 0. That is what makes the animation and the effect end together
    // rather than one outlasting the other: the last replay is the one during which the model stops
    // saying "active", and the wind-down follows within a clip length of it (a sixth of a second for a
    // Peashooter). No stage is ever cut off mid-clip.
    private boolean advancePlantFoodStage(EntitySprite sprite, Plant plant, int growthStage) {
        int stage = plantFoodStage.getOrDefault(plant, STAGE_ON);
        if (stage >= STAGE_OFF) {
            return false;
        }
        if (stage == STAGE_LOOP) {
            if (plant.isPlantFoodActive()) {
                return true;   // another turn of the loop, same stage
            }
            // A plant with no plantfood_off ends after the loop, so the sequence is however much of it
            // the art actually has. Asked through PlantStages because the clip may be a staged variant
            // ("plantfood_off_2"), which a bare hasClip would miss and cut the wind-down.
            if (PlantStages.clip(sprite, growthStage, "plantfood_off") == null) {
                return false;
            }
        }
        plantFoodStage.put(plant, stage + 1);
        return true;
    }

    private float footY(int row) {
        return lawn.worldY(row) + lawn.cellHeight() * SpritePlacer.FOOT_INSET;
    }

    private static Color tintFor(Plant plant) {
        // No octopus case: the octopus itself is drawn on top now, and tinting the plant purple as well
        // would say the same thing twice in two different visual languages.
        int chill = chillStageOf(plant);
        if (chill > 0) {
            return CHILL_TINT[Math.min(chill, CHILL_TINT.length - 1)];
        }
        return Color.WHITE;
    }
}
