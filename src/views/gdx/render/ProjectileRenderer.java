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

    public void draw(Batch batch, Projectile projectile, float alpha, float delta) {
        // Animated shots take the sprite path; everything else is a still region.
        String animated = ELEMENT_SPRITE.get(
                projectile.getElement() == null ? Element.NEUTRAL : projectile.getElement());
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
        float lane = interpolator.lane(projectile, projectile.getY(), alpha);
        float y = lawn.worldY((int) Math.round(lane)) + lawn.cellHeight() * MUZZLE_HEIGHT;

        // No time-based hold. Pinning the pea at the muzzle for a fixed delay looked right in theory
        // but peas are short-lived: many are destroyed BEFORE the delay elapses, so the shot never
        // visibly left the plant and its impact burst went off at the spawn point while damage still
        // landed downrange. muzzleAdjusted below already anchors the start of the flight to the
        // plant's mouth, and it does so by DISTANCE travelled, which cannot outlive the projectile.
        float carried = interpolatedX;
        float x = lawn.worldX(muzzleAdjusted(projectile, carried));

        if (projectile.getTrajectory() == Trajectory.LOBBED) {
            y += arcHeight(projectile, modelX);
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

    private float muzzleAdjusted(Projectile projectile, float interpolatedX) {
        Float start = launchX.get(projectile);
        if (start == null) {
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

    // Drops launch records for shots that are gone, so a long level does not accumulate them.
    public void forgetAllExcept(java.util.Set<Projectile> alive) {
        clocks.sweep();
        launchX.keySet().retainAll(alive);
        lastX.keySet().retainAll(alive);
        lastY.keySet().retainAll(alive);
    }

    // A parabola in flight distance: rises, peaks, falls. 4t(1-t) peaks at exactly 1 when t = 0.5.
    private float arcHeight(Projectile projectile, float modelX) {
        float start = launchX.getOrDefault(projectile, modelX);
        float travelled = Math.abs(modelX - start);
        float t = Math.min(1f, travelled / ARC_SPAN_CELLS);
        return 4f * ARC_HEIGHT_CELLS * lawn.cellHeight() * t * (1f - t);
    }


    // Draws a shot whose art is a PAM animation rather than a still. Returns false if the sprite is
    // unavailable, so the caller falls back to the region path and a shot is never simply invisible.
    private boolean drawAnimated(Batch batch, Projectile projectile, float alpha, float delta,
                                 String spriteName) {
        views.gdx.sprite.EntitySprite sprite = sprites == null ? null : sprites.get(spriteName);
        if (sprite == null || !sprite.isReady()) {
            return false;
        }
        String clip = views.gdx.sprite.ClipMap.firstAvailable(sprite, "animation");
        com.badlogic.gdx.math.Rectangle bounds = sprite.bounds(clip);
        if (bounds == null || bounds.width <= 0f) {
            return false;
        }

        float modelX = (float) projectile.getX();
        launchX.putIfAbsent(projectile, modelX);
        float lane = interpolator.lane(projectile, projectile.getY(), alpha);
        float y = lawn.worldY((int) Math.round(lane)) + lawn.cellHeight() * MUZZLE_HEIGHT;
        if (projectile.getTrajectory() == Trajectory.LOBBED) {
            y += arcHeight(projectile, modelX);
        }
        float x = lawn.worldX(muzzleAdjusted(projectile, interpolator.x(projectile, modelX, alpha)));

        // Sized against a WIDER target than the still peas. An animated shot's bounds cover the whole
        // effect -- the flame's trail and sparks, not just the pea at its head -- so fitting that box
        // to a pea's width shrinks the actual projectile to a speck. The box is roughly three times
        // the pea it contains.
        float scale = SpritePlacer.toSpriteSpace(SPRITE_PEA_WIDTH_CELLS * lawn.cellWidth())
                / bounds.width;
        float phase = clocks.advance(projectile, clip, delta);

        com.badlogic.gdx.math.Matrix4 previous = batch.getTransformMatrix().cpy();
        batch.setTransformMatrix(new com.badlogic.gdx.math.Matrix4(previous)
                .translate(SpritePlacer.toSpriteSpace(x), SpritePlacer.toSpriteSpace(y), 0f)
                .scale(scale, scale, 1f));
        sprite.draw(batch, clip, views.gdx.sprite.ClipMap.sample(sprite, clip, phase),
                0f, bounds.y + bounds.height / 2f, true);
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
