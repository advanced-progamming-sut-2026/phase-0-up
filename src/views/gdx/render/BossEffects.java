package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// What a Zomboss's attacks look like when they land.
//
// ## Driven off narration, like every other one-shot in this build
//
// A boss attack resolves COMPLETELY inside one call: the fireball is thrown, the plant is dealt its
// own health, the ground is scorched and the imp is standing there, all before the tick ends. There is
// no travelling projectile to draw and no state left over saying "a fireball happened here" -- so the
// sentence ZombossAttacks reports, which carries the tiles it hit, is the only thing the view can hang
// an effect on. Exactly the seam the Tomb Raiser's bones, the Dark Ages graves and the Zombotany pea
// already use. See GameScreen.onModelEvent.
//
// ## All of the art is shipped, and it made the design decisions
//
// The dump carries a full set of Zomboss effects and they map one-to-one onto the spec's attack list --
// which is the useful confirmation that these nine moves are the real game's and not an invention:
// ZOMBOSS_DARK_FIREBALL for the dragon, ZOMBOSS_MISSILE_EXPLOSION_EGYPT / _ICEAGE for the two missiles,
// ZOMBOSS_TURBINE_WIND for the sub's suction (and, borrowed, for the Tuskmaster's gale, which is the
// same picture of moving air), and ZOMBOSS_SHARK_PROJECTILE for the baby sharks.
//
// Two attacks deliberately draw nothing of their own:
//
//   * FREEZE_COLUMN adds real FrozenTerrain to every cell of the column, and TerrainRenderer has always
//     drawn those -- so the column fills with the game's own ice for free, and a burst laid on top
//     would be a second, briefer ice column arguing with it.
//   * A row burn and a dash are both "the whole of these two rows", which is drawn as a row of copies
//     rather than as one enormous sprite, for the same reason ExplosionEffects lays a Jalapeno's fire
//     out column by column: the art is authored as a single gout on a one-tile canvas.
public final class BossEffects {

    private static final String FIREBALL = "ZOMBOSS_DARK_FIREBALL";
    private static final String FIREBALL_CLIP = "impact";
    private static final String MISSILE_EGYPT = "ZOMBOSS_MISSILE_EXPLOSION_EGYPT";
    private static final String MISSILE_ICE = "ZOMBOSS_MISSILE_EXPLOSION_ICEAGE";
    private static final String MISSILE_CLIP = "missile_explosion";
    private static final String WIND = "ZOMBOSS_TURBINE_WIND";
    private static final String WIND_CLIP = "animation";
    private static final String SHARK = "ZOMBOSS_SHARK_PROJECTILE";
    private static final String SHARK_CLIP = "attack";
    // The row burn borrows the game's own lane fire, which is what ExplosionEffects already lays down
    // for a Jalapeno -- the same picture of a row going up, and the only row-length flame in the dump.
    private static final String ROW_FIRE = "JALAPENO_FIRE";
    private static final String ROW_FIRE_CLIP = "idle";

    private static final float TILE_WIDTH_CELLS = 1.6f;
    // A row effect is sized by its HEIGHT, not its width, and that is the difference between a row of
    // dust and a wall of smoke.
    //
    // These animations are authored tall: the Egypt missile burst measures 594x958, half of which is
    // the plume above the impact. Sized to 1.9 cells WIDE it came out 2.6 lanes high -- and a dash lays
    // nine of them across a row, so two rows of it covered most of the board in grey and hid the lawn
    // the player was trying to read. Capping the height instead keeps every copy inside its own lane
    // and lets the width fall out of the art's own aspect.
    private static final float ROW_HEIGHT_CELLS = 1.25f;
    private static final float LIFT_CELLS = 0.35f;
    private static final float FALLBACK_LIFETIME = 1f;

    // ---- the sentences -----------------------------------------------------------------------------
    //
    // Every one opens "The <boss name> ", and the name is skipped rather than captured: which of the
    // four bosses threw it is already settled by the level, and the ATTACK is what picks the art.

    private static final Pattern FIREBALL_AT =
            Pattern.compile("^The .+? hurls a fireball at \\((\\d+), (\\d+)\\).*$");
    private static final Pattern ROW_BURN =
            Pattern.compile("^The .+? breathes fire down rows (\\d+) and (\\d+).*$");
    private static final Pattern MISSILE_AT =
            Pattern.compile("^The .+? fires a missile into \\((\\d+), (\\d+)\\)!$");
    private static final Pattern DASH =
            Pattern.compile("^The .+? charges down rows (\\d+) and (\\d+).*$");
    private static final Pattern ICE_BOULDER =
            Pattern.compile("^The .+? slings an ice boulder into \\((\\d+), (\\d+)\\)!$");
    private static final Pattern ICE_WIND =
            Pattern.compile("^The .+? blasts a wall of ice wind down row (\\d+)!$");
    private static final Pattern SHARK_AT =
            Pattern.compile("^The .+? sends a baby shark up the lane and it swallows the .+? "
                    + "at \\((\\d+), (\\d+)\\) whole!$");
    private static final Pattern TURBINE =
            Pattern.compile("^The .+? fires up its turbine and sucks rows (\\d+) and (\\d+).*$");

    private static final class Burst {
        String sprite;
        String clip;
        float x;
        float y;
        // Exactly one of these is set. A tile burst is sized to span its tile; a row burst is sized to
        // fit its LANE and takes whatever width the art's aspect gives it. See ROW_HEIGHT_CELLS.
        float widthCells;
        float heightCells;
        // 1 for a burst on one tile; BOARD_COLS for anything that covers a whole row.
        int copies = 1;
        float age;
        float lifetime;
    }

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final List<Burst> bursts = new ArrayList<>();
    private final LocalTransform transform = new LocalTransform();
    // Clip lengths, keyed "sprite#clip". Resolved once each: a burst lasting longer than its animation
    // holds its final frame, which reads as a freeze rather than as a blast.
    private final Map<String, Float> lifetimes = new HashMap<>();

    public BossEffects(SpriteRegistry sprites, LawnGeometry lawn) {
        this.sprites = sprites;
        this.lawn = lawn;
    }

    // Offered every sentence the model drains; anything that is not a boss attack falls straight
    // through, so this sits alongside the seven other consumers reading the same stream.
    public void onEvent(String message) {
        if (message == null) {
            return;
        }
        String text = message.trim();
        if (tileBurst(text, FIREBALL_AT, FIREBALL, FIREBALL_CLIP)
                || tileBurst(text, MISSILE_AT, MISSILE_EGYPT, MISSILE_CLIP)
                || tileBurst(text, ICE_BOULDER, MISSILE_ICE, MISSILE_CLIP)
                || tileBurst(text, SHARK_AT, SHARK, SHARK_CLIP)) {
            return;
        }
        // The dash lays NOTHING on the lawn, deliberately.
        //
        // It used to lay nine missile bursts per row, which was the worst-looking effect in the game:
        // eighteen grey smoke rings covering both of the boss's rows and most of what the player was
        // trying to read. The charge is now shown where it actually happens -- the Sphinx plays its own
        // `stomp` (see ZombieActions) and the camera takes a full-strength kick (see CameraShake) --
        // and the row emptying of plants is the rest of the message.
        if (rowPairBurst(text, ROW_BURN, ROW_FIRE, ROW_FIRE_CLIP)
                || rowPairBurst(text, TURBINE, WIND, WIND_CLIP)) {
            return;
        }
        if (DASH.matcher(text).matches()) {
            return;
        }
        Matcher gale = ICE_WIND.matcher(text);
        if (gale.matches()) {
            addRow(Integer.parseInt(gale.group(1)), WIND, WIND_CLIP);
        }
    }

    // A burst on the one tile a sentence names.
    private boolean tileBurst(String text, Pattern pattern, String sprite, String clip) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.matches()) {
            return false;
        }
        int col = Integer.parseInt(matcher.group(1));
        int row = Integer.parseInt(matcher.group(2));
        Burst burst = begin(sprite, clip);
        burst.x = lawn.centerX(col);
        burst.y = lawn.centerY(row) + LIFT_CELLS * lawn.cellHeight();
        burst.widthCells = TILE_WIDTH_CELLS;
        bursts.add(burst);
        return true;
    }

    // The three attacks that cover the boss's own two rows end to end.
    private boolean rowPairBurst(String text, Pattern pattern, String sprite, String clip) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.matches()) {
            return false;
        }
        addRow(Integer.parseInt(matcher.group(1)), sprite, clip);
        addRow(Integer.parseInt(matcher.group(2)), sprite, clip);
        return true;
    }

    // One row's worth: the art laid evenly along the lane rather than stretched, because every one of
    // these animations is authored as a single one-tile gout.
    private void addRow(int row, String sprite, String clip) {
        Burst burst = begin(sprite, clip);
        burst.copies = utils.Constants.BOARD_COLS;
        burst.heightCells = ROW_HEIGHT_CELLS;
        burst.x = lawn.centerX(0);
        // The lane's own foot line rather than a lifted centre: fire and wind both run along the ground.
        burst.y = lawn.worldY(row) + lawn.cellHeight() * 0.55f;
        bursts.add(burst);
    }

    private Burst begin(String sprite, String clip) {
        Burst burst = new Burst();
        burst.sprite = sprite;
        burst.clip = clip;
        burst.lifetime = lifetimeOf(sprite, clip);
        return burst;
    }

    // Read from the art, cached per sprite-and-clip. Keyed on both because these effects do not agree
    // on a clip name -- "impact", "missile_explosion", "animation", "idle" -- so one length per sprite
    // would be one length for whichever clip happened to be asked for first.
    private float lifetimeOf(String spriteName, String clipName) {
        return lifetimes.computeIfAbsent(spriteName + '#' + clipName, key -> {
            EntitySprite sprite = sprites.get(spriteName);
            if (sprite == null || !sprite.isReady()) {
                return FALLBACK_LIFETIME;
            }
            float duration = sprite.clipDuration(resolveClip(sprite, clipName));
            return duration > 0f ? duration : FALLBACK_LIFETIME;
        });
    }

    // Ages every burst and draws it. One pass, in front of the lane: a boss attack is the loudest thing
    // on screen while it lasts, and hiding half of it behind the zombies it just killed would waste it.
    public void draw(Batch batch, float delta) {
        for (int i = bursts.size() - 1; i >= 0; i--) {
            Burst burst = bursts.get(i);
            burst.age += delta;
            if (burst.age >= burst.lifetime) {
                bursts.remove(i);
            }
        }
        for (Burst burst : bursts) {
            drawOne(batch, burst);
        }
    }

    private void drawOne(Batch batch, Burst burst) {
        EntitySprite sprite = sprites.get(burst.sprite);
        if (sprite == null || !sprite.isReady()) {
            return;
        }
        String clip = resolveClip(sprite, burst.clip);
        Rectangle bounds = sprite.bounds(clip);
        if (bounds == null || bounds.width <= 0f) {
            return;
        }
        float scale = burst.heightCells > 0f
                ? SpritePlacer.toSpriteSpace(burst.heightCells * lawn.cellHeight()) / bounds.height
                : SpritePlacer.toSpriteSpace(burst.widthCells * lawn.cellWidth()) / bounds.width;
        float sample = ClipMap.sample(sprite, clip, burst.age);
        int copies = Math.max(1, burst.copies);
        float step = (lawn.rightEdge() - lawn.originX()) / copies;
        for (int i = 0; i < copies; i++) {
            float x = copies == 1 ? burst.x : lawn.originX() + step * (i + 0.5f);
            transform.begin(batch, SpritePlacer.toSpriteSpace(x),
                    SpritePlacer.toSpriteSpace(burst.y), scale);
            sprite.draw(batch, clip, sample, 0f, bounds.y + bounds.height / 2f, true);
            transform.end(batch);
        }
    }

    // The wanted clip when the animation has it, and something sensible when it does not -- a missing
    // clip resolves to nothing in PamEntitySprite and the effect simply would not draw.
    private static String resolveClip(EntitySprite sprite, String wanted) {
        return wanted != null && sprite.hasClip(wanted)
                ? wanted
                : ClipMap.firstAvailable(sprite, "impact", "explosion", "animation", ClipMap.IDLE);
    }
}
