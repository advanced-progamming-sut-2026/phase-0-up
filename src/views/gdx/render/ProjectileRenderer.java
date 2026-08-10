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

    private TextureRegion pea;

    public ProjectileRenderer(Assets assets, LawnGeometry lawn, EntityInterpolator interpolator) {
        this.assets = assets;
        this.lawn = lawn;
        this.interpolator = interpolator;
    }

    private boolean warnedMissingRegion;

    public void draw(Batch batch, Projectile projectile, float alpha) {
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
        float x = lawn.worldX(interpolator.x(projectile, modelX, alpha));
        float lane = interpolator.lane(projectile, projectile.getY(), alpha);
        float y = lawn.worldY((int) Math.round(lane)) + lawn.cellHeight() * 0.55f;

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

        batch.setColor(previous);
    }

    // A parabola in flight distance: rises, peaks, falls. 4t(1-t) peaks at exactly 1 when t = 0.5.
    private float arcHeight(Projectile projectile, float modelX) {
        float start = launchX.computeIfAbsent(projectile, p -> modelX);
        float travelled = Math.abs(modelX - start);
        float t = Math.min(1f, travelled / ARC_SPAN_CELLS);
        return 4f * ARC_HEIGHT_CELLS * lawn.cellHeight() * t * (1f - t);
    }

    // Shots are short-lived and numerous; without this the launch map would grow for the whole level.
    public void forget(Projectile projectile) {
        launchX.remove(projectile);
    }

    public void sweep(java.util.function.Predicate<Projectile> stillAlive) {
        launchX.keySet().removeIf(p -> !stillAlive.test(p));
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
