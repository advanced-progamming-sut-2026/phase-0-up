package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

import java.util.ArrayList;
import java.util.List;

// Short bursts where a shot lands.
//
// The model has no notion of an impact -- Projectile.onHit applies damage and sets isDestroyed, and
// the object is swept the same tick -- so there is no event to listen to. The view infers it instead:
// GameRenderer notices a projectile that was in flight last frame and is gone this frame, and asks
// for a burst at its last drawn position. That covers zombie hits, grave hits and shots that expire
// on the board edge alike, without the model growing an "impact happened" flag it does not need.
//
// The burst is the game's own splat ANIMATION (T_SPLAT_PEA_ROCK), played once. Earlier versions drew a
// ring of generated discs, then a ring of scattered copies of one static splat frame; both were
// stand-ins for an animation that was in the dump all along.
public final class ImpactEffects {

    // One splat animation per element. These live under 768/INITIAL/EFFECTS/, not 768/FULL/ -- a search
    // of FULL alone finds only T_SPLAT_PEA_ROCK and concludes, wrongly, that ice and fire have no splat
    // of their own. Both trees have to be searched.
    private static final java.util.Map<models.entities.projectiles.Element, String> SPLAT_BY_ELEMENT =
            new java.util.EnumMap<>(models.entities.projectiles.Element.class);

    private static final String SPLAT_DEFAULT = "T_SPLAT_PEA";

    static {
        SPLAT_BY_ELEMENT.put(models.entities.projectiles.Element.NEUTRAL, "T_SPLAT_PEA");
        SPLAT_BY_ELEMENT.put(models.entities.projectiles.Element.ICE, "T_SPLAT_SNOW_PEA");
        SPLAT_BY_ELEMENT.put(models.entities.projectiles.Element.FIRE, "T_SPLAT_FIRE_PEA");
    }

    // Clip variants, so repeated hits do not look stamped from one mould. T_SPLAT_PEA ships six;
    // firstAvailable falls back for the splats that ship fewer.
    private static final String[] CLIPS = {"animation", "animation2", "animation3"};

    // The splat art is authored far larger than a pea impact needs (its box is ~240 PAM units wide),
    // so it is scaled down to roughly a third of a tile.
    private static final float SPLAT_WIDTH_CELLS = 0.34f;

    private static final float LIFETIME = 0.30f;

    private static final class Burst {
        float x;
        float y;
        float age;
        String clip;
        String sprite;
        Color color;
    }

    private final SpriteRegistry sprites;
    private final List<Burst> bursts = new ArrayList<>();
    private int nextClip;

    public ImpactEffects(SpriteRegistry sprites) {
        this.sprites = sprites;
    }

    // World position, in the same space the projectile was drawn in.
    public void spawn(float worldX, float worldY, Color color,
                      models.entities.projectiles.Element element) {
        Burst burst = new Burst();
        burst.x = worldX;
        burst.y = worldY;
        burst.color = new Color(color);
        burst.sprite = SPLAT_BY_ELEMENT.getOrDefault(
                element == null ? models.entities.projectiles.Element.NEUTRAL : element,
                SPLAT_DEFAULT);
        // Cycled rather than random, so identical runs stay pixel-reproducible for the screenshot
        // harness -- the same reason the old burst used fixed angles.
        burst.clip = CLIPS[nextClip++ % CLIPS.length];
        bursts.add(burst);
    }

    public void draw(Batch batch, float delta, float cellSize) {
        if (bursts.isEmpty()) {
            return;
        }
        Color previous = batch.getColor().cpy();

        float width = SpritePlacer.toSpriteSpace(SPLAT_WIDTH_CELLS * cellSize);

        for (int i = bursts.size() - 1; i >= 0; i--) {
            Burst burst = bursts.get(i);
            burst.age += delta;
            if (burst.age >= LIFETIME) {
                bursts.remove(i);
                continue;
            }
            float t = burst.age / LIFETIME;
            // Fades out over its life. The clip itself is short, so without this the last frame would
            // pop rather than dissolve.
            batch.setColor(burst.color.r, burst.color.g, burst.color.b, 1f - t);
            drawSplat(batch, burst, width, t);
        }
        batch.setColor(previous);
    }

    private void drawSplat(Batch batch, Burst burst, float width, float t) {
        EntitySprite splat = sprites.get(burst.sprite);
        if (splat == null || !splat.isReady()) {
            return;
        }
        String clip = ClipMap.firstAvailable(splat, burst.clip);
        com.badlogic.gdx.math.Rectangle bounds = splat.bounds(clip);
        if (bounds == null || bounds.width <= 0f) {
            return;
        }
        float scale = width / bounds.width;

        com.badlogic.gdx.math.Matrix4 previous = batch.getTransformMatrix().cpy();
        com.badlogic.gdx.math.Matrix4 scaled = new com.badlogic.gdx.math.Matrix4(previous)
                .translate(SpritePlacer.toSpriteSpace(burst.x),
                        SpritePlacer.toSpriteSpace(burst.y), 0f)
                .scale(scale, scale, 1f);

        batch.setTransformMatrix(scaled);
        // Same y-down correction as everywhere else: libPVZ reports bounds in the .PAM's Flash-style
        // coordinates, where the art hangs below the origin.
        splat.draw(batch, clip, ClipMap.sample(splat, clip, t * LIFETIME),
                0f, bounds.y + bounds.height / 2f, true);
        batch.setTransformMatrix(previous);
    }
}
