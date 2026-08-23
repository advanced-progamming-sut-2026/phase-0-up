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
                plantFoodStage.remove(plant);
                lastStage.remove(plant);
                growing.remove(plant);
                idlePhase.remove(plant);
                idleVariant.remove(plant);
                lastStrike.remove(plant);
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
        java.util.Map<String, Boolean> parts =
                PlantDamage.visibilityFor(sprite, plant.getName(), damageStage);
        SpritePlacer.drawStanding(batch, sprite, clip, stateTime, cx, fy, true, parts);

        drawHitFlash(batch, sprite, plant, clip, stateTime, cx, fy, parts, delta);

        if (plant.hasOctopus()) {
            drawOctopus(batch, plant, cx, fy, delta);
        }

        batch.setColor(previous);
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
            // Plant food is a three-part sequence, so the end of one stage starts the next rather than
            // ending the whole thing.
            if (plantFood.contains(plant) && advancePlantFoodStage(sprite, plant)) {
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
        // No octopus case: the octopus itself is drawn on top now, and tinting the plant purple as well
        // would say the same thing twice in two different visual languages.
        int chill = plant.getChillLevel();
        if (chill > 0) {
            return CHILL_TINT[Math.min(chill, CHILL_TINT.length - 1)];
        }
        return Color.WHITE;
    }
}
