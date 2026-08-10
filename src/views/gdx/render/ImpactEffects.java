package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import views.gdx.core.Assets;

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
// Drawn from the shared 1x1 white texture rather than a particle atlas: a handful of expanding,
// fading quads reads perfectly well at this size and costs one draw call each, with nothing to load
// or dispose.
public final class ImpactEffects {

    private static final float LIFETIME = 0.22f;
    private static final int SHARDS = 5;

    // Radius the burst expands to, as a fraction of a cell.
    private static final float SPREAD = 0.32f;
    private static final float SHARD_SIZE = 9f;

    private static final class Burst {
        float x;
        float y;
        float age;
        Color color;
    }

    private final Assets assets;
    private final List<Burst> bursts = new ArrayList<>();
    private Drawable fill;

    public ImpactEffects(Assets assets) {
        this.assets = assets;
    }

    // World position, in the same space the projectile was drawn in.
    public void spawn(float worldX, float worldY, Color color) {
        Burst burst = new Burst();
        burst.x = worldX;
        burst.y = worldY;
        burst.color = new Color(color);
        bursts.add(burst);
    }

    public void draw(Batch batch, float delta, float cellSize) {
        if (bursts.isEmpty()) {
            return;
        }
        if (fill == null) {
            fill = assets.round(Color.WHITE);
        }
        Color previous = batch.getColor().cpy();

        for (int i = bursts.size() - 1; i >= 0; i--) {
            Burst burst = bursts.get(i);
            burst.age += delta;
            if (burst.age >= LIFETIME) {
                bursts.remove(i);
                continue;
            }
            float t = burst.age / LIFETIME;
            float radius = SPREAD * cellSize * t;
            float size = SHARD_SIZE * (1f - t * 0.55f);

            batch.setColor(burst.color.r, burst.color.g, burst.color.b, 1f - t);
            for (int s = 0; s < SHARDS; s++) {
                // Fixed angles rather than random, so a burst is stable frame to frame and identical
                // runs stay pixel-reproducible for the screenshot harness.
                double angle = (Math.PI * 2 * s) / SHARDS + t * 0.6;
                float px = burst.x + (float) Math.cos(angle) * radius;
                float py = burst.y + (float) Math.sin(angle) * radius * 0.7f;
                fill.draw(batch, SpritePlacer.toSpriteSpace(px) - size / 2f,
                        SpritePlacer.toSpriteSpace(py) - size / 2f, size, size);
            }
        }
        batch.setColor(previous);
    }
}
