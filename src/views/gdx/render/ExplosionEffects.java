package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
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

    // A radioactive sun caught in mid-air blows up too, but with its own art: SUN_BOMB's "attack"
    // clip, which is what that animation ships the burst as. One layer only -- unlike the Cherry Bomb
    // there is no separate rear half.
    private static final String SUN_BOMB_SPRITE = "SUN_BOMB";
    private static final String SUN_BOMB_CLIP = "attack";
    private static final String SUN_BOMB_NAME = "Radioactive sun";

    // The blast covers a 3x3 (5x5 for a sun bomb), so it is drawn a little wider than three tiles --
    // an explosion that exactly matches its damage box reads as smaller than it hits.
    private static final float WIDTH_CELLS = 4.2f;

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

    // Jalapeno does not explode in a 3x3 -- it burns its whole LANE, which is what its
    // explosionColRadius of 9 in plants.json means. A Cherry Bomb blast on its tile showed a plant
    // whose entire point is the row doing something local, so it gets the game's own row flame instead,
    // repeated once per column because JALAPENO_FIRE is authored as a single gout on a 390 canvas.
    private static final String JALAPENO_NAME = "Jalapeno";
    private static final String JALAPENO_SPRITE = "JALAPENO_FIRE";
    private static final String JALAPENO_CLIP = "idle";
    private static final float JALAPENO_WIDTH_CELLS = 1.9f;

    // How fresh a blast has to be to have caused a death. A detonation kills on the tick it happens,
    // and its event is queued BEFORE the deaths it causes -- the ability fires during the plant pass
    // and processDeaths runs at the end of the same tick, so both arrive in one drain. A tight window
    // is therefore not a guess: it is the difference between "this blast killed it" and "something else
    // died near a blast that went off half a second ago".
    private static final float KILL_WINDOW_SECONDS = 0.12f;

    // Blast footprints, in cells either side of the centre. The model's real radii live in plants.json
    // and the view does not read them; these match the three cases this class already distinguishes.
    private static final int DEFAULT_RADIUS = 1;      // 3x3
    private static final int SUN_BOMB_RADIUS = 2;     // 5x5

    private static final class Blast {
        float x;
        float y;
        // The tile it went off on, for asking what it caught.
        int col;
        int row;
        // Jalapeno burns its whole lane rather than a box around itself.
        boolean laneWide;
        int radius = DEFAULT_RADIUS;
        float age;
        // Null for the default two-layer plant explosion; set for effects with their own art.
        String sprite;
        String clip;
        // How many copies to lay across the lane, and how wide each is. 1 and 0 mean "one, at the
        // default blast width", which is every explosion except the Jalapeno's row burn.
        int copies = 1;
        float widthCells;
    }

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final List<Blast> blasts = new ArrayList<>();
    // A Jalapeno's row burn is nine copies drawn twice a frame, so this class was on its own producing
    // eighteen throwaway matrices per frame for as long as the fire lasted. See LocalTransform.
    private final LocalTransform transform = new LocalTransform();

    // Consulted only for the sun bomb, to find where a falling sun was actually drawn.
    private CollectibleRenderer collectibles;

    public ExplosionEffects(SpriteRegistry sprites, LawnGeometry lawn) {
        this.sprites = sprites;
        this.lawn = lawn;
    }

    void setCollectibles(CollectibleRenderer collectibles) {
        this.collectibles = collectibles;
    }

    private static void logBlast(int col, int row) {
        if (views.gdx.core.DebugFlags.BOARD_COUNTS) {
            com.badlogic.gdx.Gdx.app.log("Explosion", "blast at (" + col + ", " + row + ")");
        }
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
            blast.col = col;
            blast.row = row;
            String plant = matcher.group(1).trim();
            if (JALAPENO_NAME.equalsIgnoreCase(plant)) {
                blast.sprite = JALAPENO_SPRITE;
                blast.clip = JALAPENO_CLIP;
                blast.laneWide = true;
                blast.copies = utils.Constants.BOARD_COLS;
                blast.widthCells = JALAPENO_WIDTH_CELLS;
                // The lane's own foot line, not a lifted centre: fire runs along the ground.
                blast.y = lawn.worldY(row) + lawn.cellHeight() * 0.55f;
                blasts.add(blast);
                logBlast(col, row);
                return;
            }
            boolean ownArt = SUN_BOMB_NAME.equalsIgnoreCase(plant);
            if (ownArt) {
                blast.sprite = SUN_BOMB_SPRITE;
                blast.clip = SUN_BOMB_CLIP;
                blast.radius = SUN_BOMB_RADIUS;
                // Blow up WHERE THE SUN WAS DRAWN, not on the tile it was filed under. A falling sun is
                // drawn several cells higher than its tile, so the tile is not where the player saw it.
                float[] drawn = collectibles == null ? null
                        : collectibles.lastRadioactivePosition(col, row);
                if (drawn != null) {
                    blast.x = drawn[0];
                    blast.y = drawn[1];
                    blasts.add(blast);
                    logBlast(col, row);
                    return;
                }
            }
            // The lift is for the Cherry Bomb ONLY. Its art carries the "KA-BOOF!" lettering in the
            // upper half, so centring it on the tile put the text among the zombies' feet. The sun
            // bomb's burst is centred on itself, and lifting it made the explosion float above the sun
            // that produced it.
            blast.y = lawn.centerY(row) + (ownArt ? 0f : LIFT_CELLS * lawn.cellHeight());
            blasts.add(blast);
            logBlast(col, row);
        } catch (NumberFormatException ignored) {
            // a message that looks like a detonation but is not; nothing to draw
        }
    }

    // Did a blast that just went off cover this tile?
    //
    // This is how a zombie's death is told apart from an EXPLOSIVE death, which is the difference
    // between it falling over and it being reduced to ash. The model does not record how a zombie died
    // -- HealthComponent.applyDamage takes an Element and keeps only the attacker -- so rather than
    // widen the model for a cosmetic distinction, the view answers it from what it already tracks:
    // where every live blast is. The same reasoning that makes a vanished projectile an impact.
    //
    // Only blasts inside KILL_WINDOW_SECONDS count, so a corpse falling next to a blast that went off
    // half a second ago is not mistaken for one caught in it.
    boolean killedByBlast(int col, int row) {
        for (Blast blast : blasts) {
            if (blast.age > KILL_WINDOW_SECONDS) {
                continue;
            }
            if (blast.laneWide) {
                if (blast.row == row) {
                    return true;
                }
                continue;
            }
            if (Math.abs(blast.col - col) <= blast.radius
                    && Math.abs(blast.row - row) <= blast.radius) {
                return true;
            }
        }
        return false;
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
        draw(batch, REAR_SPRITE, false);
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
        draw(batch, TOP_SPRITE, true);
    }

    // `layer` is the default two-layer plant blast's sprite for this pass, or null for the front pass
    // of a blast that brings its own art. A blast with its own sprite draws ONCE, on the front pass.
    private void draw(Batch batch, String layer, boolean front) {
        for (Blast blast : blasts) {
            if (blast.sprite != null) {
                if (front) {
                    drawOne(batch, blast, blast.sprite, blast.clip);
                }
                continue;   // its own art replaces both default layers
            }
            drawOne(batch, blast, layer, null);
        }
    }

    private void drawOne(Batch batch, Blast blast, String spriteName, String wantedClip) {
        EntitySprite sprite = sprites.get(spriteName);
        if (sprite == null || !sprite.isReady()) {
            return;
        }
        // "explosion", not "animation". The blast animations name their clips explosion/explosion2/
        // explosion3 -- asking for "animation" first meant firstAvailable fell through to a clip that
        // does not exist, PamEntitySprite could not resolve it, and nothing was drawn at all.
        String clip = wantedClip != null && sprite.hasClip(wantedClip)
                ? wantedClip
                : ClipMap.firstAvailable(sprite, "explosion", "animation", ClipMap.IDLE);
        Rectangle bounds = sprite.bounds(clip);
        if (bounds == null || bounds.width <= 0f) {
            return;
        }
        float width = blast.widthCells > 0f ? blast.widthCells : WIDTH_CELLS;
        float scale = SpritePlacer.toSpriteSpace(width * lawn.cellWidth()) / bounds.width;

        // One copy sits on the blast's own tile; several are laid evenly across the whole lane, which
        // is what a row burn is.
        int copies = Math.max(1, blast.copies);
        float step = (lawn.rightEdge() - lawn.originX()) / copies;
        float sample = ClipMap.sample(sprite, clip, blast.age);

        for (int i = 0; i < copies; i++) {
            float x = copies == 1 ? blast.x : lawn.originX() + step * (i + 0.5f);
            transform.begin(batch, SpritePlacer.toSpriteSpace(x),
                    SpritePlacer.toSpriteSpace(blast.y), scale);
            sprite.draw(batch, clip, sample, 0f, bounds.y + bounds.height / 2f, true);
            transform.end(batch);
        }
    }
}
