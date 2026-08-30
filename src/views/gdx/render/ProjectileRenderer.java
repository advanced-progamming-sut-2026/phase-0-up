package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import models.entities.projectiles.Element;
import models.entities.projectiles.Projectile;
import models.entities.projectiles.Trajectory;
import views.gdx.bridge.EntityInterpolator;
import views.gdx.core.Assets;
import views.gdx.map.LawnGeometry;

import java.util.IdentityHashMap;
import java.util.Map;

// Draws shots in flight.
//
// The interesting part is the arc. The phase-2 spec requires lobbed shots to travel in a visible
// parabola, but the model has no arc: Projectile.move() only ever changes x (and, for bouncing bowling
// bulbs, lane). Nothing in the simulation knows how high a melon is.
//
// That is fine, because the height is purely cosmetic -- collision is resolved on x alone. So the arc
// is generated here from how far the shot has travelled, and the model is left untouched. This is
// exactly the kind of thing that belongs in the view.
public final class ProjectileRenderer {

    // How far a lobbed shot is assumed to travel, in cells, for the purpose of shaping its arc. Real
    // flights vary; what matters is that the shot rises and falls convincingly rather than matching a
    // predicted landing spot the model never computes.
    private static final float ARC_SPAN_CELLS = 3.2f;
    private static final float ARC_HEIGHT_CELLS = 0.85f;

    // The shipped projectile art, per element.
    //
    // These are real sub-images out of the plants' own animations -- the pea a Peashooter spits is the
    // part named "peashooter_stem_pea", and Snow Pea carries its own blue one. Both were found with
    // -Dpvz.dumpParts and confirmed with -Dpvz.probeRegions before being named here.
    //
    // This replaces a tinted-disc fake. The disc existed because the only pea sprite anyone had found
    // was green, and multiplying green by cyan is still green, so a Snow Pea fired a visibly normal pea.
    // The answer was never a better tint -- it was that the blue pea is in the dump.
    private static final String PEA_GREEN = "IMAGE_PLANT_PEASHOOTER_PEASHOOTER_23X23";
    private static final String PEA_ICE = "IMAGE_PLANT_SNOWPEA_SNOWPEA_23X23";
    // The fire pea is an ANIMATION, not a still. Pinning it to one sub-image froze the flame; the
    // T_FIRE_PEA PAM is what the flicker lives in. Elements listed here are drawn through the sprite
    // path below instead of as a region.
    private static final Map<Element, String> ELEMENT_SPRITE = new java.util.EnumMap<>(Element.class);

    static {
        ELEMENT_SPRITE.put(Element.FIRE, "T_FIRE_PEA");
    }

    // The same flame in blue, for a shot boosted by a Torchwood that has eaten plant food.
    private static final String BLUE_FIRE_PEA = "T_FIRE_PEA_BLUE";

    // Pea Pod's plant food throws one of these per head. The dump ships it as its own effect rather
    // than as a scaled-up pea, so a giant pea is not merely a big green circle.
    private static final String GIANT_PEA = "PEAPOD_PLANTFOOD_GIANTPEA";

    // Fume-shroom's cloud, and the spike a plant-food'd Cactus fires from then on.
    private static final String FUME_BUBBLES = "FUMESHROOM_BUBBLES";
    private static final String CACTUS_BOOSTED_SPIKE = "CACTUS_PROJECTILE_PLANTFOOD";

    // Citron's ordinary citrus orb and the plasma orb its plant food fires.
    private static final String CITRUS_ORB = "CITRON_CITRUS_ORB";
    private static final String CITRUS_PLASMA_ORB = "CITRON_PLANTFOOD_ORB";

    // Shots whose art belongs to the PLANT rather than to an element, checked first.
    //
    // Element is the wrong axis for these: a rutabaga and a corn kernel are both NEUTRAL, so both were
    // drawn as a green pea. Rotobaga ships two flight animations (PROJECTILE1 and PROJECTILE2, the
    // second being the return trip in the real game); the first is the one that leaves the plant.
    // Every type the plant data actually authors gets its own flight art. Element is the wrong axis for
    // all of them: a cabbage, a thorn, a star, a melon and a corn kernel are all NEUTRAL, so keying on
    // element drew every one of them as a green pea -- a Cabbage-pult lobbing peas, a Cactus firing
    // peas. The type is what the plant chose and the type is what the dump has art for.
    private static final Map<models.entities.projectiles.ProjectileType, String> TYPE_SPRITE =
            new java.util.EnumMap<>(models.entities.projectiles.ProjectileType.class);

    static {
        // PROJECTILE2, not PROJECTILE1. The first is authored on a 105x12 canvas -- a motion streak
        // nine times as long as it is tall -- and a streak drawn on a shot that is climbing or falling
        // diagonally reads as a separate shot flying straight down the lane. It also made the rutabaga
        // impossible to size: the vegetable is a small part of a very wide box, so the box had to be
        // made a whole cell across for the veg to be visible, and the shot then sat half a box to the
        // right of the plant that fired it. The second is authored on 38x14, which is the vegetable.
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.RUTABAGA,
                "ROTORUTABAGA_PROJECTILE2");
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.CORN_KERNEL,
                "T_KERNALPULT_PROJECTILE");
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.BUTTER,
                "T_KERNALPULT_PROJECTILE");
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.CABBAGE,
                "T_CABBAGEPULT_PROJECTILE");
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.MELON,
                "T_MELON_PROJECTILE");
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.WINTER_MELON,
                "T_WINTERMELON_PROJECTILE");
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.PEPPER,
                "T_PEPPERPULT_PROJECTILE");
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.THORN,
                "T_CACTUS_PROJECTILE");
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.STAR,
                "T_STARFRUIT_PROJECTILE");
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.SPORE,
                "T_PUFFSHROOM_PROJECTILE");
        // Fume-shroom's own bubbles, not the Spore-shroom's. Nothing else in the roster fires FUME.
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.FUME, FUME_BUBBLES);
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.GOO,
                "GOOPEASHOOTER_PROJECTILES");
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.GRAPE,
                "GRAPESHOT_PROJECTILE");
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.BOWLING_BULB,
                "BOWLINGBULB_PROJECTILE1");
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.PLASMA_BALL,
                "BOWLINGBULB_PLANTFOOD_PROJECTILE");
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.CITRUS_ORB, CITRUS_ORB);
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.CITRUS_PLASMA_ORB,
                CITRUS_PLASMA_ORB);
        // A Cactus that has eaten plant food is upgraded for the rest of the level, and the dump ships
        // the spike it fires afterwards. It was drawing Red Stinger's projectile -- a plant that is not
        // in this roster -- because PIERCING_SPIKE is only ever produced by that upgrade.
        TYPE_SPRITE.put(models.entities.projectiles.ProjectileType.PIERCING_SPIKE,
                CACTUS_BOOSTED_SPIKE);
    }

    // Fired at the plant's mouth on the frame a shot first appears. Only Rotobaga ships one; anything
    // else simply has no entry and no burst is drawn.
    private static final Map<models.entities.projectiles.ProjectileType, String> TYPE_MUZZLE =
            new java.util.EnumMap<>(models.entities.projectiles.ProjectileType.class);

    static {
        TYPE_MUZZLE.put(models.entities.projectiles.ProjectileType.RUTABAGA,
                "ROTORUTABAGA_MUZZLE_BURST");
    }

    private static final Map<Element, String> ELEMENT_REGION = new java.util.EnumMap<>(Element.class);

    static {
        ELEMENT_REGION.put(Element.NEUTRAL, PEA_GREEN);
        ELEMENT_REGION.put(Element.ICE, PEA_ICE);
    }

    // How big a pea is drawn, as a fraction of a tile's width.
    //
    // NOT the region's own pixel size. The atlas is packed at 0.643 of the authored size, so a pea
    // authored as 23x23 arrives here as a 15x15 region -- drawing it at that size made peas about an
    // eighth of a tile, which is what "the peas are too small" was about. Sizing against the lawn
    // instead means every element's pea reads the same regardless of how its source was packed, and
    // the fire pea (authored 43x43) does not come out three times the size of a normal one.
    private static final float PEA_WIDTH_CELLS = 0.34f;

    // The same pea, but measured across an effect box that also contains its trail.
    private static final float SPRITE_PEA_WIDTH_CELLS = 1.05f;

    // Per-sprite override of that width.
    //
    // 1.05 cells is right for the fire pea, whose bounds are mostly flame trailing behind a small pea
    // -- fit the BOX to a pea's width and the pea itself becomes a speck. A corn kernel has no trail,
    // so its box is the kernel, and the same 1.05 made it the size of the plant that threw it.
    private static final Map<String, Float> SPRITE_WIDTH_CELLS = Map.ofEntries(
            Map.entry("T_KERNALPULT_PROJECTILE", 0.40f),
            Map.entry("ROTORUTABAGA_PROJECTILE2", 0.55f),
            // Thrown by a plant-food'd Pea Pod, and the whole point of it is that it is enormous --
            // roughly three peas across, carrying twenty peas' worth of damage.
            Map.entry(GIANT_PEA, 0.95f),
            // Citron's charged shot is a heavy thing, and the plasma orb its plant food fires is
            // heavier still -- it is about to go through an entire lane without stopping.
            Map.entry(CITRUS_ORB, 0.70f),
            Map.entry(CITRUS_PLASMA_ORB, 0.95f),
            // The lobbed fruit are big things a plant heaves overhead -- roughly two thirds of a tile,
            // sized against each other rather than against a pea so a melon outweighs a cabbage.
            Map.entry("T_CABBAGEPULT_PROJECTILE", 0.55f),
            Map.entry("T_MELON_PROJECTILE", 0.72f),
            Map.entry("T_WINTERMELON_PROJECTILE", 0.72f),
            // The pepper is FIRE, so its box is mostly the flame trailing behind it and only a little
            // of it is the pepper. Measured like the melon beside it -- 0.55 across the whole box --
            // the pepper itself came out a third the size of the fruit the other pults throw.
            Map.entry("T_PEPPERPULT_PROJECTILE", 0.95f),
            // Small, fast, and fired flat.
            Map.entry("T_CACTUS_PROJECTILE", 0.34f),
            Map.entry("T_STARFRUIT_PROJECTILE", 0.62f),
            Map.entry("T_PUFFSHROOM_PROJECTILE", 0.42f),
            Map.entry("T_SPORESHROOM_PROJECTILE", 0.50f),
            // A fume is a cloud rather than a pellet, and it is authored on a full 390 plant canvas.
            Map.entry(FUME_BUBBLES, 0.90f),
            Map.entry(CACTUS_BOOSTED_SPIKE, 0.40f),
            Map.entry("GOOPEASHOOTER_PROJECTILES", 0.36f),
            Map.entry("GRAPESHOT_PROJECTILE", 0.30f),
            // One entry per colour: the width is looked up by the name that is actually drawn, and a
            // blue bulb falling through to the default was drawn twice the size of the cyan one.
            Map.entry("BOWLINGBULB_PROJECTILE1", 0.55f),
            Map.entry("BOWLINGBULB_PROJECTILE2", 0.55f),
            Map.entry("BOWLINGBULB_PROJECTILE3", 0.55f),
            Map.entry("BOWLINGBULB_PLANTFOOD_PROJECTILE", 0.60f),
            Map.entry("T_REDSTINGER_PROJECTILE", 0.36f));

    // Elements with no dedicated sprite in the dump still have to read apart, so those keep a tint over
    // the green pea. Ice and neutral are NOT tinted any more: they have their own art.
    private static final Map<Element, Color> ELEMENT_TINT = new java.util.EnumMap<>(Element.class);

    static {
        ELEMENT_TINT.put(Element.FIRE, new Color(1f, 0.55f, 0.15f, 1f));
        ELEMENT_TINT.put(Element.POISON, new Color(0.70f, 0.45f, 1f, 1f));
        ELEMENT_TINT.put(Element.BUTTER, new Color(1f, 0.92f, 0.35f, 1f));
    }

    private final Assets assets;
    private final LawnGeometry lawn;
    private final EntityInterpolator interpolator;

    // Where each shot started, so its arc has a phase. Identity-keyed for the same reason the
    // interpolator is: every Projectile is constructed with id = 0.
    private final Map<Projectile, Float> launchX = new IdentityHashMap<>();


    // How long the pea waits at the muzzle. Matched to PlantRenderer.ATTACK_SECONDS (0.45s) so the
    // release lands inside the attack animation rather than after it has finished.


    private TextureRegion pea;
    // White fill used to colour non-neutral shots; see the draw method.
    private com.badlogic.gdx.scenes.scene2d.utils.Drawable wash;

    private final views.gdx.sprite.SpriteRegistry sprites;
    private final AnimationClocks clocks = new AnimationClocks();

    public ProjectileRenderer(Assets assets, LawnGeometry lawn, EntityInterpolator interpolator,
                              views.gdx.sprite.SpriteRegistry sprites) {
        this.assets = assets;
        this.lawn = lawn;
        this.interpolator = interpolator;
        this.sprites = sprites;
    }

    private boolean warnedMissingRegion;

    // laneZombies is the row this shot is flying down, so a lobbed one can be aimed. May be null.
    public void draw(Batch batch, Projectile projectile, float alpha, float delta,
                     java.util.List<models.entities.zombies.Zombie> laneZombies) {
        noteArcSpan(projectile, laneZombies);
        noteMuzzle(projectile);
        // The plant's own art first, then the element's, then a still region. A rutabaga and a corn
        // kernel are NEUTRAL, so keying on element alone drew both as a green pea.
        String animated = projectile.getType() == null ? null : TYPE_SPRITE.get(projectile.getType());
        if (animated == null) {
            animated = ELEMENT_SPRITE.get(
                    projectile.getElement() == null ? Element.NEUTRAL : projectile.getElement());
        }
        // A shot that comes in numbered forms is drawn as the one that was actually fired: Bowling Bulb
        // rolls a cyan, blue or orange bulb and the dump ships all three, but every bulb in the game
        // came out as BOWLINGBULB_PROJECTILE1 because the type alone cannot tell them apart.
        animated = variantOf(animated, projectile.getArtVariant());
        // A giant pea is its own effect, whatever the shooter would otherwise have thrown.
        if (projectile.isGiant()) {
            animated = GIANT_PEA;
        }
        // A pea that crossed a blue-flamed Torchwood burns blue. The dump ships the same effect in both
        // colours, authored on the same 450 canvas with the same clips, so it needs no size of its own.
        if (projectile.isBlueFlame()) {
            animated = BLUE_FIRE_PEA;
        }
        if (animated != null && drawAnimated(batch, projectile, alpha, delta, animated)) {
            return;
        }
        TextureRegion region = peaRegion(projectile.getElement());
        if (region == null) {
            // Silently drawing nothing is the worst outcome here: shots keep damaging zombies while
            // being invisible, which reads as the game cheating. Say so, once.
            if (!warnedMissingRegion) {
                warnedMissingRegion = true;
                com.badlogic.gdx.Gdx.app.error("ProjectileRenderer",
                        PEA_GREEN + " unavailable -- shots will be invisible");
            }
            return;
        }

        float modelX = (float) projectile.getX();
        // Remembered on first sight: both the muzzle blend and the lobbed arc need to know where this
        // shot started, and the model does not expose its spawn point.
        launchX.putIfAbsent(projectile, modelX);
        float interpolatedX = interpolator.x(projectile, modelX, alpha);
        if (!onLawn(laneOf(projectile, alpha))) {
            return;
        }
        float y = laneY(projectile, alpha);

        // No time-based hold. Pinning the pea at the muzzle for a fixed delay looked right in theory
        // but peas are short-lived: many are destroyed BEFORE the delay elapses, so the shot never
        // visibly left the plant and its impact burst went off at the spawn point while damage still
        // landed downrange. muzzleAdjusted below already anchors the start of the flight to the
        // plant's mouth, and it does so by DISTANCE travelled, which cannot outlive the projectile.
        float carried = interpolatedX;
        float x = lawn.worldX(muzzleAdjusted(projectile, carried));

        if (projectile.getTrajectory() == Trajectory.LOBBED) {
            // The INTERPOLATED x, not the model's. x was already being smoothed to 60 fps while the
            // height was recomputed from a position that only changes ten times a second, so the shot
            // slid horizontally and climbed in six visible steps -- the two halves of one parabola
            // running at different rates. This is what made the arc look jerky; the arc itself was
            // always the right shape.
            y += arcHeight(projectile, interpolatedX);
        }

        Color previous = batch.getColor().cpy();
        // Only elements without their own sprite are tinted. A green pea and a blue pea are now two
        // different images, so neither needs colouring.
        batch.setColor(ELEMENT_TINT.getOrDefault(projectile.getElement(), Color.WHITE));

        // Drawn inside GameRenderer's scaled pass, so world coordinates have to be converted the same
        // way SpritePlacer converts them -- otherwise the shot lands at 0.643 of where it should.
        // Sized against the lawn, keeping the source's aspect ratio, so packing scale cannot leak into
        // how big a pea looks.
        float w = SpritePlacer.toSpriteSpace(PEA_WIDTH_CELLS * lawn.cellWidth());
        float h = w * region.getRegionHeight() / (float) region.getRegionWidth();
        float sx = SpritePlacer.toSpriteSpace(x);
        float sy = SpritePlacer.toSpriteSpace(y);

        batch.draw(region, sx - w / 2f, sy - h / 2f, w, h);

        // The white disc that used to be stamped over non-neutral shots is gone. It was a workaround for
        // not having the blue pea, and it always looked like what it was -- a coloured blob sitting on
        // top of a pea. Real art needs nothing drawn over it.

        batch.setColor(previous);

        // The IMPACT must use the model's real position, not the muzzle-adjusted one the pea is drawn
        // at. Early in its flight the pea is pulled back toward the plant's mouth, so recording the
        // drawn x made the burst go off up to half a cell short -- visibly before the shot reached the
        // grave it had actually just hit.
        lastX.put(projectile, lawn.worldX(modelX));
        lastY.put(projectile, y);
    }


    // Last drawn world position, so ImpactEffects can burst exactly where the pea vanished.
    private final Map<Projectile, Float> lastX = new IdentityHashMap<>();
    private final Map<Projectile, Float> lastY = new IdentityHashMap<>();

    public float lastDrawnX(Projectile p) {
        return lastX.getOrDefault(p, 0f);
    }

    public float lastDrawnY(Projectile p) {
        return lastY.getOrDefault(p, 0f);
    }

    public Color tintOf(Projectile p) {
        return ELEMENT_TINT.getOrDefault(p.getElement(), Color.WHITE);
    }

    // Where a shot LOOKS like it comes from, which is not where the model starts it.
    //
    // Plants sit at column + 0.5 (PlantFactory) and ShootProjectileAbility spawns at owner.getX()
    // + 0.5, so a pea is born a full cell to the right of the plant that fired it: it pops into
    // existence over the next tile instead of leaving the muzzle. That offset is deliberate on the
    // model's side (collision starts clear of the shooter) and is not ours to change.
    //
    // So the view pulls the first stretch of the flight back toward the plant's mouth and releases it
    // as the shot travels. By MUZZLE_BLEND_CELLS the drawn position is the model position again, so
    // impacts still land exactly where collision says. This is the spec's "accurate projectile spawns"
    // item, done without touching gameplay.
    private static final float MUZZLE_OFFSET_CELLS = 0.62f;
    private static final float MUZZLE_BLEND_CELLS = 0.9f;
    private static final float MUZZLE_HEIGHT = 0.62f;   // fraction of a cell: roughly mouth height

    // Where this shot sits vertically, as a CONTINUOUS lane rather than a row index.
    //
    // Rotobaga and Starfruit fire diagonally -- half a lane per tick -- and rounding the interpolated
    // lane back to an integer threw that away twice over: once when the interpolator sampled the
    // model's rounded row, and again here. The result was a diagonal shot that crossed the board in
    // whole-lane jumps. The model's own exactY is what the interpolator now tracks, so this only has
    // to stop discarding it.
    private float laneY(Projectile projectile, float alpha) {
        return lawn.worldY(laneOf(projectile, alpha)) + lawn.cellHeight() * MUZZLE_HEIGHT;
    }

    private float laneOf(Projectile projectile, float alpha) {
        return interpolator.lane(projectile, projectile.getY(), alpha);
    }

    // Whether this shot is still over the lawn.
    //
    // The model keeps a shot alive until its ROUNDED row leaves the board, which is up to a lane and a
    // half past the edge -- Math.round(-0.5) is 0, so a diagonal climbing out of the top row survives
    // several more ticks. That was invisible while the view rounded the lane too and drew it on row 0;
    // now that the diagonal is drawn where the model actually puts it, those ticks paint a shot on the
    // background above the lawn.
    //
    // Half a lane of tolerance, so a shot still reads as leaving the board rather than blinking out on
    // the edge line. This is a drawing decision only: the shot is alive and still collides.
    private boolean onLawn(float lane) {
        return lane >= -0.5f && lane <= utils.Constants.BOARD_ROWS - 0.5f;
    }

    private float muzzleAdjusted(Projectile projectile, float interpolatedX) {
        Float start = launchX.get(projectile);
        if (start == null) {
            return interpolatedX;
        }
        // Diagonals are exempt. The pull-back compensates a model-side spawn offset along x, and it
        // leans toward the launch point -- which for a shot travelling LEFT means pushing it right. A
        // Rotobaga fires two of each, so its four rutabagas came out fanned to one side of the plant
        // instead of out of it. Their abilities now spawn on the plant's own tile, so there is nothing
        // left here to correct.
        if (Math.abs(projectile.getSpeedY()) > 1e-6) {
            return interpolatedX;
        }
        float travelled = Math.abs(interpolatedX - start);
        if (travelled >= MUZZLE_BLEND_CELLS) {
            return interpolatedX;
        }
        // Full pull-back at the muzzle, easing to none by the end of the blend.
        float easing = 1f - (travelled / MUZZLE_BLEND_CELLS);
        float direction = Math.signum(interpolatedX - start);
        if (direction == 0f) {
            direction = 1f;
        }
        return interpolatedX - direction * MUZZLE_OFFSET_CELLS * easing;
    }

    // Muzzle flashes waiting to be handed to ImpactEffects, which owns every one-shot effect on the
    // board. Queued rather than drawn here because this renderer runs per lane inside the entity pass,
    // and an effect drawn there would be covered by whatever is drawn after it.
    // The shooter's name rides along so the sound can be its own -- see AudioManager.forEntity. Null
    // for a shot with no plant behind it, which the audio side treats as "use the generic cue".
    public record Muzzle(float x, float y, String sprite, String shooter) { }

    private final java.util.List<Muzzle> pendingMuzzles = new java.util.ArrayList<>();

    // The frame a shot first appears is the frame its plant fired, so that is when the flash goes off.
    // launchX is the record of "seen before", and it is not written until later in draw().
    private void noteMuzzle(Projectile projectile) {
        if (launchX.containsKey(projectile) || projectile.getType() == null) {
            return;
        }
        String sprite = TYPE_MUZZLE.get(projectile.getType());
        if (sprite == null) {
            return;
        }
        // The plant's mouth, which is where muzzleAdjusted pulls the first frame of the flight back to.
        float x = lawn.worldX((float) projectile.getX() - MUZZLE_OFFSET_CELLS);
        float y = lawn.worldY(projectile.getY()) + lawn.cellHeight() * MUZZLE_HEIGHT;
        models.entities.plants.Plant shooter = projectile.getShooter();
        pendingMuzzles.add(new Muzzle(x, y, sprite, shooter == null ? null : shooter.getName()));
    }

    // Handed over once per frame and cleared. Never accumulates: a flash not collected this frame would
    // be collected the next.
    public java.util.List<Muzzle> drainMuzzles() {
        if (pendingMuzzles.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<Muzzle> out = java.util.List.copyOf(pendingMuzzles);
        pendingMuzzles.clear();
        return out;
    }

    // Drops launch records for shots that are gone, so a long level does not accumulate them.
    public void forgetAllExcept(java.util.Set<Projectile> alive) {
        clocks.sweep();
        launchX.keySet().retainAll(alive);
        arcSpan.keySet().retainAll(alive);
        lastX.keySet().retainAll(alive);
        lastY.keySet().retainAll(alive);
    }

    // A parabola in flight distance: rises, peaks, falls. 4t(1-t) peaks at exactly 1 when t = 0.5.
    //
    // Takes the drawn x rather than the model's, so the height is sampled at the same 60 fps the
    // horizontal travel is. Both callers pass the interpolated value.
    private float arcHeight(Projectile projectile, float x) {
        float start = launchX.getOrDefault(projectile, x);
        float travelled = Math.abs(x - start);
        float t = travelled / arcSpan.getOrDefault(projectile, ARC_SPAN_CELLS);
        // Past its target the shot keeps falling instead of pinning to the ground. Clamping t at 1
        // made the curve stop dead at zero height, so a shot that outlived its arc -- one whose target
        // died, or a lob that flew further than measured -- changed from falling to sliding in a
        // single frame. Squaring the overshoot lets it keep going down at the speed it was already
        // going, which reads as a shot that landed rather than one that gave up.
        if (t > 1f) {
            float over = t - 1f;
            return -4f * ARC_HEIGHT_CELLS * lawn.cellHeight() * over * (1f + over);
        }
        return 4f * ARC_HEIGHT_CELLS * lawn.cellHeight() * t * (1f - t);
    }

    // How far this particular shot has to fly, so it comes DOWN on what it was aimed at.
    //
    // ARC_SPAN_CELLS was a fixed 3.2 for every lob, which is only right when the target happens to be
    // 3.2 cells away. Anything further and the melon finished its arc in mid-lawn and then slid along
    // the ground to its target, which is what "it slides after hitting" was: the arc had already
    // landed. Measured once, at the shot's first frame -- re-measuring every frame would make the arc
    // twitch as the target walks, and a zombie barely moves during a lob anyway.
    private final Map<Projectile, Float> arcSpan = new IdentityHashMap<>();

    private void noteArcSpan(Projectile projectile, java.util.List<models.entities.zombies.Zombie> lane) {
        if (projectile.getTrajectory() != Trajectory.LOBBED || arcSpan.containsKey(projectile)) {
            return;
        }
        float launch = (float) projectile.getX();
        // A shot fired AT a grave carries its aim point, so there is nothing to guess: it has to come
        // down on that tile or the flash goes off with the melon still in the air.
        if (projectile.isTerrainSeeking()) {
            arcSpan.put(projectile, Math.max(0.5f, (float) projectile.getTerrainTargetX() - launch));
            return;
        }
        float nearest = Float.MAX_VALUE;
        if (lane != null) {
            for (models.entities.zombies.Zombie zombie : lane) {
                float dx = (float) zombie.getMovement().getPositionX() - launch;
                // Ahead of the shot, and far enough that a zombie already on top of the plant does not
                // collapse the arc to nothing.
                if (dx > 0.5f && dx < nearest) {
                    nearest = dx;
                }
            }
        }
        arcSpan.put(projectile, nearest < Float.MAX_VALUE ? nearest : ARC_SPAN_CELLS);
    }


    // Draws a shot whose art is a PAM animation rather than a still. Returns false if the sprite is
    // unavailable, so the caller falls back to the region path and a shot is never simply invisible.
    // Which clip to ask each projectile sprite for.
    //
    // Almost all of them call their only clip "animation", which is why this used to be that one name
    // hard-coded. GRAPESHOT_PROJECTILE does not: it ships a clip per direction of travel
    // (animation_forward, animation_backward, animation_verticle_up/_down -- the misspelling is the
    // dump's) and NO plain "animation". firstAvailable then fell through to its last-resort candidate,
    // bounds() came back null for a clip the sprite does not have, and drawAnimated returned false --
    // so every grape in the game was quietly drawn as a green pea, with the real art sitting unused.
    //
    // Forward, because a grape is launched down the lane at whatever it is chasing. The vertical
    // variants belong to a bounce that this build does not animate separately.
    //
    // Citron's two orbs are the same story with a different spelling: each names its only clip after
    // itself ("Citron_Citrus_Orb", "Plantfood_Citron_Plasma_Orb") and has no "animation" at all.
    private static final Map<String, String[]> SPRITE_CLIPS = Map.of(
            "GRAPESHOT_PROJECTILE", new String[] {"animation_forward", "animation"},
            // The fume cloud's clips are "special" (the puff) and "plantfood"; it has no "animation"
            // and no "idle" either, so without this it would fall through to nothing.
            FUME_BUBBLES, new String[] {"special", "animation"},
            CITRUS_ORB, new String[] {"Citron_Citrus_Orb", "animation"},
            CITRUS_PLASMA_ORB, new String[] {"Plantfood_Citron_Plasma_Orb", "animation"});

    // Swaps the trailing number of a numbered sprite name for the one this shot actually is. Only the
    // names that END in a digit can be varied -- BOWLINGBULB_PROJECTILE1 becomes ...2 or ...3 -- so
    // every other shot passes through untouched however its ability numbers itself.
    private static String variantOf(String spriteName, int variant) {
        if (spriteName == null || variant <= 0) {
            return spriteName;
        }
        char last = spriteName.charAt(spriteName.length() - 1);
        if (last < '1' || last > '9') {
            return spriteName;
        }
        return spriteName.substring(0, spriteName.length() - 1) + variant;
    }

    private static String[] clipsFor(String spriteName) {
        return SPRITE_CLIPS.getOrDefault(spriteName, new String[] {"animation"});
    }

    private boolean drawAnimated(Batch batch, Projectile projectile, float alpha, float delta,
                                 String spriteName) {
        views.gdx.sprite.EntitySprite sprite = sprites == null ? null : sprites.get(spriteName);
        if (sprite == null || !sprite.isReady()) {
            return false;
        }
        String clip = views.gdx.sprite.ClipMap.firstAvailable(sprite, clipsFor(spriteName));
        com.badlogic.gdx.math.Rectangle bounds = sprite.bounds(clip);
        if (bounds == null || bounds.width <= 0f) {
            return false;
        }

        float modelX = (float) projectile.getX();
        launchX.putIfAbsent(projectile, modelX);
        if (!onLawn(laneOf(projectile, alpha))) {
            return true;   // handled: off the lawn is a decision to draw nothing, not a failure
        }
        float y = laneY(projectile, alpha);
        float interpolatedX = interpolator.x(projectile, modelX, alpha);
        if (projectile.getTrajectory() == Trajectory.LOBBED) {
            y += arcHeight(projectile, interpolatedX);   // see the still-region path
        }
        float x = lawn.worldX(muzzleAdjusted(projectile, interpolatedX));

        // Sized against a WIDER target than the still peas. An animated shot's bounds cover the whole
        // effect -- the flame's trail and sparks, not just the pea at its head -- so fitting that box
        // to a pea's width shrinks the actual projectile to a speck. The box is roughly three times
        // the pea it contains.
        float widthCells = SPRITE_WIDTH_CELLS.getOrDefault(spriteName, SPRITE_PEA_WIDTH_CELLS);
        float scale = SpritePlacer.toSpriteSpace(widthCells * lawn.cellWidth()) / bounds.width;
        float phase = clocks.advance(projectile, clip, delta);

        com.badlogic.gdx.math.Matrix4 previous = batch.getTransformMatrix().cpy();
        batch.setTransformMatrix(new com.badlogic.gdx.math.Matrix4(previous)
                .translate(SpritePlacer.toSpriteSpace(x), SpritePlacer.toSpriteSpace(y), 0f)
                .scale(scale, scale, 1f));
        // CENTRED on the shot's position, in x as well as in y.
        //
        // x used to be left at the art's own origin, which for these effect PAMs is the LEFT EDGE of
        // the box rather than its middle -- unlike a plant or a zombie, which are authored centred on
        // their canvas, and which is why nothing else needed this. The shot was therefore drawn half a
        // box to the right of where the model says it is: a fraction of a pea for a pea, half a tile
        // for a wide effect box. That is why Rotobaga's rutabagas came out beside the plant instead of
        // out of it, and why making the art bigger pushed them further out still.
        //
        // Self-cancelling for art that IS authored centred (bounds.x is then -width/2), so this is a
        // correction for both conventions rather than a second guess about which one applies.
        sprite.draw(batch, clip, views.gdx.sprite.ClipMap.sample(sprite, clip, phase),
                -(bounds.x + bounds.width / 2f), bounds.y + bounds.height / 2f, true);
        batch.setTransformMatrix(previous);

        lastX.put(projectile, lawn.worldX(modelX));
        lastY.put(projectile, y);
        return true;
    }

    // Cached per element: region() walks the bank's index, and this runs per shot per frame.
    private final Map<Element, TextureRegion> regionCache = new java.util.EnumMap<>(Element.class);

    private TextureRegion peaRegion(Element element) {
        Element key = element == null ? Element.NEUTRAL : element;
        TextureRegion cached = regionCache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            // Elements with no sprite of their own fall back to the green pea, which is then tinted.
            TextureRegion region = assets.region(ELEMENT_REGION.getOrDefault(key, PEA_GREEN));
            if (region == null) {
                region = assets.region(PEA_GREEN);
            }
            if (region != null) {
                regionCache.put(key, region);
            }
            return region;
        } catch (RuntimeException e) {
            com.badlogic.gdx.Gdx.app.error("ProjectileRenderer", "no pea region for " + key, e);
            return null;
        }
    }
}
