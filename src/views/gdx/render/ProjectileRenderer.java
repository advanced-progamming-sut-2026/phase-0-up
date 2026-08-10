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

    private static final String DEFAULT_PEA = "IMAGE_PROJECTILEPEA";

    // Element decides the colour, which is what the spec means by projectiles being "visually
    // distinct": a fire pea, an ice pea and a goo pea read apart instantly even sharing one sprite.
    private static final Map<Element, Color> ELEMENT_TINT = new java.util.EnumMap<>(Element.class);

    static {
        ELEMENT_TINT.put(Element.NEUTRAL, new Color(0.55f, 0.95f, 0.35f, 1f));
        ELEMENT_TINT.put(Element.FIRE, new Color(1f, 0.55f, 0.15f, 1f));
        ELEMENT_TINT.put(Element.ICE, new Color(0.55f, 0.90f, 1f, 1f));
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

    public ProjectileRenderer(Assets assets, LawnGeometry lawn, EntityInterpolator interpolator) {
        this.assets = assets;
        this.lawn = lawn;
        this.interpolator = interpolator;
    }

    private boolean warnedMissingRegion;

    public void draw(Batch batch, Projectile projectile, float alpha, float delta) {
        TextureRegion region = peaRegion();
        if (region == null) {
            // Silently drawing nothing is the worst outcome here: shots keep damaging zombies while
            // being invisible, which reads as the game cheating. Say so, once.
            if (!warnedMissingRegion) {
                warnedMissingRegion = true;
                com.badlogic.gdx.Gdx.app.error("ProjectileRenderer",
                        DEFAULT_PEA + " unavailable -- shots will be invisible");
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
        batch.setColor(ELEMENT_TINT.getOrDefault(projectile.getElement(), Color.WHITE));

        // Drawn inside GameRenderer's scaled pass, so world coordinates have to be converted the same
        // way SpritePlacer converts them -- otherwise the shot lands at 0.643 of where it should.
        float w = region.getRegionWidth();
        float h = region.getRegionHeight();
        float sx = SpritePlacer.toSpriteSpace(x);
        float sy = SpritePlacer.toSpriteSpace(y);
        batch.draw(region, sx - w / 2f, sy - h / 2f, w, h);

        // pvz-assets ships exactly one projectile sprite at 768 -- IMAGE_PROJECTILEPEA, and it is
        // GREEN. A SpriteBatch tint multiplies, so green x cyan is still green: no tint can turn that
        // sprite blue, which is why a Snow Pea kept firing a visibly normal pea.
        //
        // The overlay therefore has to come from a WHITE source, where multiplying by the element
        // colour yields that colour exactly. Drawn slightly smaller than the pea so its silhouette
        // still reads as a pea rather than a square.
        Element element = projectile.getElement();
        if (element != null && element != Element.NEUTRAL) {
            if (wash == null) {
                wash = assets.round(Color.WHITE);
            }
            Color c = ELEMENT_TINT.getOrDefault(element, Color.WHITE);
            // Nearly opaque, and a DISC rather than a stretched pixel -- the square overlay was what
            // made an ice pea look like a green pea wearing a cyan rectangle.
            batch.setColor(c.r, c.g, c.b, 0.95f);
            float d = Math.min(w, h) * 0.92f;
            wash.draw(batch, sx - d / 2f, sy - d / 2f, d, d);
        }

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


    private TextureRegion peaRegion() {
        if (pea == null) {
            try {
                pea = assets.region(DEFAULT_PEA);
            } catch (RuntimeException e) {
                com.badlogic.gdx.Gdx.app.error("ProjectileRenderer", "no " + DEFAULT_PEA, e);
            }
        }
        return pea;
    }
}
