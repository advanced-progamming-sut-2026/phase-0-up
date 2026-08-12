package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The blast when a plant detonates.
//
// Driven off the model's narration rather than inferred. A projectile's disappearance can stand in for
// its impact, but a plant consumed by its own blast looks exactly like one that was eaten -- both are
// simply dead and swept on the same tick. So AreaExplosiveAbility.detonate says so, and this listens.
//
// Drawn in TWO layers, which is how the art ships: CHERRYBOMB_EXPLOSION_REAR goes behind the plants and
// zombies and TOP goes in front, so the blast wraps around whatever is caught in it. Using only the
// front layer makes the explosion sit on top of everything like a sticker.
public final class ExplosionEffects {

    // "Cherry Bomb detonates at (3, 2)!" -- the sentence AreaExplosiveAbility.detonate emits. The plant
    // name is captured but unused for now; every exploding plant currently borrows the Cherry Bomb
    // blast, which is the only one whose two layers are both in the dump.
    private static final Pattern DETONATION =
            Pattern.compile("^(.+?) detonates at \\((\\d+), (\\d+)\\)!$");

    private static final String REAR_SPRITE = "CHERRYBOMB_EXPLOSION_REAR";
    private static final String TOP_SPRITE = "CHERRYBOMB_EXPLOSION_TOP";

    // The blast covers a 3x3, so it is drawn about three tiles across.
    private static final float WIDTH_CELLS = 3.2f;

    // Lifted off the tile centre. The art is authored around the blast's own middle, but the "KA-BOOF!"
    // lettering sits in its upper half, so centring on the tile puts the text down among the zombies'
    // feet instead of over their heads.
    private static final float LIFT_CELLS = 0.45f;

    // Fallback only. A blast actually lives exactly as long as its animation, read from the art -- a
    // fixed lifetime longer than the clip makes the explosion play, finish, and then sit there holding
    // its last frame, which reads as a freeze rather than a blast.
    private static final float FALLBACK_LIFETIME = 0.9f;
    private float lifetime = FALLBACK_LIFETIME;
    private boolean lifetimeResolved;

    private static final class Blast {
        float x;
        float y;
        float age;
    }

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final List<Blast> blasts = new ArrayList<>();

    public ExplosionEffects(SpriteRegistry sprites, LawnGeometry lawn) {
        this.sprites = sprites;
        this.lawn = lawn;
    }

    // Offered every event the model drains. Anything that is not a detonation is ignored, so this can
    // sit alongside the toast overlay reading the same stream.
    public void onEvent(String message) {
        if (message == null) {
            return;
        }
        Matcher matcher = DETONATION.matcher(message.trim());
        if (!matcher.matches()) {
            return;
        }
        try {
            int col = Integer.parseInt(matcher.group(2));
            int row = Integer.parseInt(matcher.group(3));
            Blast blast = new Blast();
            blast.x = lawn.centerX(col);
            blast.y = lawn.centerY(row) + LIFT_CELLS * lawn.cellHeight();
            blasts.add(blast);
            if (views.gdx.core.DebugFlags.BOARD_COUNTS) {
                com.badlogic.gdx.Gdx.app.log("Explosion", "blast at (" + col + ", " + row + ")");
            }
        } catch (NumberFormatException ignored) {
            // a message that looks like a detonation but is not; nothing to draw
        }
    }

    // Behind the board. Called before the lanes are drawn.
    public void drawRear(Batch batch, float delta) {
        resolveLifetime();
        // Ages are advanced here only, so the two passes stay on the same clock -- advancing in both
        // would run every blast at double speed.
        for (int i = blasts.size() - 1; i >= 0; i--) {
            Blast blast = blasts.get(i);
            blast.age += delta;
            if (blast.age >= lifetime) {
                blasts.remove(i);
            }
        }
        draw(batch, REAR_SPRITE);
    }

    // The blast lasts exactly as long as the longer of its two layers.
    private void resolveLifetime() {
        if (lifetimeResolved) {
            return;
        }
        lifetimeResolved = true;
        float longest = 0f;
        for (String name : new String[] {REAR_SPRITE, TOP_SPRITE}) {
            EntitySprite sprite = sprites.get(name);
            if (sprite != null && sprite.isReady()) {
                longest = Math.max(longest,
                        sprite.clipDuration(ClipMap.firstAvailable(sprite, "explosion", "animation")));
            }
        }
        if (longest > 0f) {
            lifetime = longest;
        }
    }

    // In front of the board. Called after the lanes are drawn.
    public void drawFront(Batch batch) {
        draw(batch, TOP_SPRITE);
    }

    private void draw(Batch batch, String spriteName) {
        if (blasts.isEmpty()) {
            return;
        }
        EntitySprite sprite = sprites.get(spriteName);
        if (sprite == null || !sprite.isReady()) {
            return;
        }
        // "explosion", not "animation". The blast animations name their clips explosion/explosion2/
        // explosion3 -- asking for "animation" first meant firstAvailable fell through to a clip that
        // does not exist, PamEntitySprite could not resolve it, and nothing was drawn at all.
        String clip = ClipMap.firstAvailable(sprite, "explosion", "animation", ClipMap.IDLE);
        Rectangle bounds = sprite.bounds(clip);
        if (bounds == null || bounds.width <= 0f) {
            return;
        }
        float scale = SpritePlacer.toSpriteSpace(WIDTH_CELLS * lawn.cellWidth()) / bounds.width;

        for (Blast blast : blasts) {
            Matrix4 previous = batch.getTransformMatrix().cpy();
            Matrix4 scaled = new Matrix4(previous)
                    .translate(SpritePlacer.toSpriteSpace(blast.x),
                            SpritePlacer.toSpriteSpace(blast.y), 0f)
                    .scale(scale, scale, 1f);
            batch.setTransformMatrix(scaled);
            sprite.draw(batch, clip, ClipMap.sample(sprite, clip, blast.age),
                    0f, bounds.y + bounds.height / 2f, true);
            batch.setTransformMatrix(previous);
        }
    }
}
