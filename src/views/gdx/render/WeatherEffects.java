package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
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

// What the WORLD does to a lawn: Ancient Egypt's tornado, Frostbite's freezing wind, and the Dark Ages
// ground opening under a necromancy tile.
//
// Driven off the model's narration, the same way ExplosionEffects is, and for the same reason: none of
// them leaves anything on the board to infer it from. A tornado's whole effect is that some of the
// wave's zombies are already deep on the lawn instead of walking in from the edge -- which, without
// this, looks exactly like zombies spawning in the wrong place. The freezing wind chills every plant in
// a row and then is gone; the only trace is that a row of plants is suddenly a shade bluer. And a
// necromancy zombie simply APPEARS in the middle of the board, which is indistinguishable from a bug
// unless the tile it came out of visibly opens.
//
// Every message is a sentence the model already emits for the terminal build, so nothing was added to
// the model to make any of this work -- which is also the standing risk, and why the sentences are
// pinned by a test: reword one in the model and the effect silently stops happening.
public final class WeatherEffects {

    // WaveSystem.applyEgyptTornado announces the storm, and then emits one of these per zombie it
    // drops. The DROP line is what gets drawn: it names the lane and how many columns past the spawn
    // edge the zombie landed, which is the tile the sand is actually arriving on.
    private static final Pattern TORNADO_DROP =
            Pattern.compile("^The tornado drops .+ into lane (\\d+), (\\d+) column\\(s\\) past the edge\\.$");
    // EnvironmentSystem.applyFreezingWind, one per gusted row.
    private static final Pattern CHILL_WIND =
            Pattern.compile("^A freezing wind sweeps through row (\\d+)\\.$");
    // EnvironmentSystem.applyNecromancy, one per necromancy tile that raises a zombie at wave start.
    // Column first, then row -- the model writes its coordinates that way everywhere.
    private static final Pattern NECROMANCY_RISE = Pattern.compile(
            "^A zombie claws up from a necromancy grave at \\((\\d+), (\\d+)\\)\\.$");
    // EnvironmentSystem.surfaceLowTideZombies. The beach's version of the same ambush.
    private static final Pattern LOW_TIDE_RISE = Pattern.compile(
            "^A zombie surfaces from the low tide at \\((\\d+), (\\d+)\\)\\.$");

    // Egypt's sandstorm ships in the same two halves the Cherry Bomb's blast does. A storm has nothing
    // to wrap around -- it is weather, in front of everything -- so both halves go in front, together:
    // each on its own is a few thin wisps, and the pair is a wall of sand, which is what the model is
    // narrating when it drops four zombies into the middle of the lawn.
    private static final String SANDSTORM = "SANDSTORM_TOP";
    private static final String SANDSTORM_REAR = "SANDSTORM_REAR";
    private static final String SANDSTORM_CLIP = "loop";
    private static final String CHILL_WIND_SPRITE = "FROSTBITE_CHILL_WIND";
    private static final String CHILL_WIND_CLIP = "animation";

    // The Dark Ages ground opening. Two halves again, and for the same reason as the sandstorm: the
    // beam says magic and the dirt says something came UP through the flagstones, and only together do
    // they read as a zombie having climbed out rather than as a spell going off on an empty tile.
    //
    // TOMBSTONE_DARK_SPAWN_EFFECT is the same mark TerrainRenderer stands on the tile permanently. That
    // is deliberate rather than lazy: the thing flaring is the thing that was sitting there, which is
    // what ties the warning the player has been looking at all level to the zombie that just used it.
    private static final String TOMB_SPAWN = "TOMBSTONE_DARK_SPAWN_EFFECT";
    private static final String TOMB_SPAWN_CLIP = "animation";
    private static final String SPAWN_DIRT = "DIRT_SPAWN_DIRT";
    private static final String SPAWN_DIRT_CLIP = "tomb_dirt_anim";

    // Big Wave Beach's version of the same ambush: a zombie coming up out of a low sand bank. Same two
    // halves, same reasoning -- `ripple_exit` is the ring TerrainRenderer leaves resting on that tile,
    // breaking as something rises through it, and the splash is what makes it an event.
    private static final String LOW_TIDE_BREAK = "WATER_ZOMBIE_RIPPLE";
    private static final String LOW_TIDE_BREAK_CLIP = "ripple_exit";
    private static final String LOW_TIDE_SPLASH = "WATER_SPLASH";
    private static final String LOW_TIDE_SPLASH_CLIP = "water_splash_01";

    private static final float STORM_SECONDS = 2.2f;
    private static final float GUST_SECONDS = 3.2f;
    private static final float RISE_SECONDS = 1.7f;
    // Bigger than the tile, both of them. A burst confined to its own square looks like a decal
    // changing; spilling over the edges is what makes it an event happening ON the board.
    private static final float RISE_WIDTH_CELLS = 1.9f;
    private static final float DIRT_WIDTH_CELLS = 2.1f;
    // The splash is authored tall (527x636 against the ripple's 171x53), so it is given a narrower box:
    // scaled to the same width as the ripple it would stand four cells high and read as a geyser.
    private static final float BREAK_WIDTH_CELLS = 2.0f;
    private static final float SPLASH_WIDTH_CELLS = 1.1f;

    // ONE gust, spanning the lane and then some.
    //
    // This was three copies at 3.6 cells each, on the belief -- written into the comment that used to be
    // here -- that the art is a small 390-unit puff which has to be repeated to cover a row. It is not:
    // -Dpvz.dumpParts reports FROSTBITE_CHILL_WIND at 4320x489, which is a lane-length gust drawn on a
    // lane-length canvas. Scaling that down to 3.6 cells is a 93% reduction, and three slivers of a
    // near-white gust on Frostbite Caves' near-white ice is what "the wind is not visible" was.
    //
    // Twelve cells rather than nine, so the gust runs off both ends of the board instead of starting and
    // stopping inside it -- weather arrives from somewhere.
    private static final float GUST_WIDTH_CELLS = 12f;
    private static final int GUST_COPIES = 1;

    // And a colour, because the art is white and so is the world it blows through.
    //
    // Every other effect here is drawn as authored, which is right when the art and the ground disagree
    // -- sand on flagstone, a purple beam on stone. A white gust over white ice is the one case where
    // "as authored" is invisible, so it is tinted the deep cyan the world's own ice shadows use. Same
    // judgement as the low-sand wash: a marker is only as visible as its contrast with the floor.
    private static final Color CHILL_WIND_TINT = new Color(0.12f, 0.42f, 0.95f, 1f);

    // Two gusts, not one, stacked on the same lane.
    //
    // The same trick the sandstorm uses on a tile, for the same reason: one pass of a soft, mostly
    // transparent gust is a haze, and two at different points of the animation is weather. Raised as two
    // separate effects rather than through `copies`, because `copies` SPREADS along the lane for a
    // lane-wide effect -- that would give two half-gusts side by side instead of one dense one.
    private static final int GUST_LAYERS = 2;
    // How far the second layer is started into the clip, so the two do not move as one sheet.
    private static final float GUST_LAYER_OFFSET = 0.55f;

    // The sandstorm is drawn ON THE DROP TILE and nowhere else -- one burst per zombie the tornado
    // actually put down, on that zombie's own square.
    //
    // It was previously a board-wide wall of sand from column 4 rightward, on the reasoning that the
    // drops land somewhere in that half. That is the difference between weather and an EVENT: what the
    // player has to be able to read off the board is "sand burst HERE, and that is why a Gargantuar is
    // suddenly standing on column 6" -- a curtain across half the lawn says a storm happened but not
    // where, which is the one thing it was raised to explain.
    // Three copies on the SAME tile, not spread along the row: for a tile burst `copies` stacks rather
    // than sweeps (see drawOne). One copy of a 275x320 gust scaled to two cells is a wisp -- which is
    // what the first attempt at this looked like, a faintly warmer tile and nothing more. Layering the
    // same gust at three animation phases is what made the old board-wide version read as sand, and it
    // works the same way in one square.
    private static final float STORM_WIDTH_CELLS = 2.2f;
    private static final int STORM_COPIES = 3;

    // Each copy runs a little behind the one to its left, so a lane-wide gust reads as travelling
    // rather than as three identical puffs appearing at once.
    private static final float COPY_LAG_SECONDS = 0.18f;

    // Fade in and out over this fraction of the effect's life, so neither one pops.
    private static final float FADE = 0.25f;
    private static final float PEAK_ALPHA = 1f;

    private static final class Weather {
        float age;
        float lifetime;
        String sprite;
        String clip;
        float widthCells;
        // The lane this is happening in. Never -1 any more: both effects belong to a row.
        int row;
        // The column this sits on, or -1 to sweep the whole lane in `copies` steps.
        int col;
        int copies;
        // Multiplied into the batch colour. White for everything drawn as authored.
        Color tint;
        // How far into the clip this effect starts. Only the stacked chill-wind layers use it.
        float clipPhase;
    }

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final List<Weather> active = new ArrayList<>();

    public WeatherEffects(SpriteRegistry sprites, LawnGeometry lawn) {
        this.sprites = sprites;
        this.lawn = lawn;
    }

    // How many effects are running, and where. Package-private, for the test that pins the model's
    // sentences against the patterns above -- the same seam TerrainRenderer.sweepFlashes uses. Nothing
    // in the game asks.
    int activeCount() {
        return active.size();
    }

    boolean hasEffectAt(String sprite, int row, int col) {
        return active.stream().anyMatch(w -> w.sprite.equals(sprite) && w.row == row && w.col == col);
    }

    // Offered every event the model produces. Anything that is not weather is ignored, so this can be
    // wired to the same stream the explosions and the toasts read.
    public void onEvent(String message) {
        if (message == null) {
            return;
        }
        Matcher drop = TORNADO_DROP.matcher(message);
        if (drop.matches()) {
            int lane = Integer.parseInt(drop.group(1));
            // WaveSystem puts the zombie at ZOMBIE_SPAWN_X - columnsAhead. That x is off-board by half
            // a cell (the spawn point is 9.5), so the TILE it stands on is the floor of it.
            int col = (int) Math.floor(utils.Constants.ZOMBIE_SPAWN_X
                    - Integer.parseInt(drop.group(2)));
            col = Math.max(0, Math.min(utils.Constants.BOARD_COLS - 1, col));
            // Both halves, front and rear together: one alone is a few wisps and the pair is a burst
            // of sand. Neither wraps around anything -- weather goes in front.
            raise(SANDSTORM_REAR, SANDSTORM_CLIP, STORM_SECONDS, STORM_WIDTH_CELLS, lane, col,
                    STORM_COPIES);
            raise(SANDSTORM, SANDSTORM_CLIP, STORM_SECONDS, STORM_WIDTH_CELLS, lane, col,
                    STORM_COPIES);
            return;
        }
        Matcher gust = CHILL_WIND.matcher(message);
        if (gust.matches()) {
            int lane = Integer.parseInt(gust.group(1));
            for (int layer = 0; layer < GUST_LAYERS; layer++) {
                raise(CHILL_WIND_SPRITE, CHILL_WIND_CLIP, GUST_SECONDS, GUST_WIDTH_CELLS,
                        lane, -1, GUST_COPIES, CHILL_WIND_TINT, layer * GUST_LAYER_OFFSET);
            }
            return;
        }
        Matcher rise = NECROMANCY_RISE.matcher(message);
        if (rise.matches()) {
            int col = Integer.parseInt(rise.group(1));
            int lane = Integer.parseInt(rise.group(2));
            // Dirt first, then the beam over it: the zombie is coming up through the ground, not
            // materialising on top of a pile of it.
            raise(SPAWN_DIRT, SPAWN_DIRT_CLIP, RISE_SECONDS, DIRT_WIDTH_CELLS, lane, col, 1);
            raise(TOMB_SPAWN, TOMB_SPAWN_CLIP, RISE_SECONDS, RISE_WIDTH_CELLS, lane, col, 1);
            return;
        }
        Matcher surfaced = LOW_TIDE_RISE.matcher(message);
        if (surfaced.matches()) {
            int col = Integer.parseInt(surfaced.group(1));
            int lane = Integer.parseInt(surfaced.group(2));
            // The ring breaking first, the splash over it -- the water is displaced by the thing coming
            // up, not the other way round.
            raise(LOW_TIDE_BREAK, LOW_TIDE_BREAK_CLIP, RISE_SECONDS, BREAK_WIDTH_CELLS, lane, col, 1);
            raise(LOW_TIDE_SPLASH, LOW_TIDE_SPLASH_CLIP, RISE_SECONDS, SPLASH_WIDTH_CELLS, lane, col, 1);
        }
    }

    private void raise(String sprite, String clip, float lifetime, float widthCells, int row,
                       int col, int copies) {
        raise(sprite, clip, lifetime, widthCells, row, col, copies, Color.WHITE);
    }

    private void raise(String sprite, String clip, float lifetime, float widthCells, int row,
                       int col, int copies, Color tint) {
        raise(sprite, clip, lifetime, widthCells, row, col, copies, tint, 0f);
    }

    // `clipPhase` starts the animation partway in, so two layers of the same gust on the same lane are
    // two different moments of it rather than one sheet drawn twice.
    private void raise(String sprite, String clip, float lifetime, float widthCells, int row,
                       int col, int copies, Color tint, float clipPhase) {
        Weather weather = new Weather();
        weather.clipPhase = clipPhase;
        weather.sprite = sprite;
        weather.clip = clip;
        weather.lifetime = lifetime;
        weather.widthCells = widthCells;
        weather.row = row;
        weather.col = col;
        weather.copies = Math.max(1, copies);
        weather.tint = tint == null ? Color.WHITE : tint;
        active.add(weather);
        if (views.gdx.core.DebugFlags.BOARD_COUNTS) {
            com.badlogic.gdx.Gdx.app.log("Weather", sprite + " at row " + row
                    + (col < 0 ? " across the lane" : " column " + col));
        }
    }

    // Drawn over the lawn, after everything standing on it. Weather is not something the zombies are
    // in front of.
    public void draw(Batch batch, float delta) {
        for (int i = active.size() - 1; i >= 0; i--) {
            Weather weather = active.get(i);
            weather.age += delta;
            if (weather.age >= weather.lifetime) {
                active.remove(i);
                continue;
            }
            drawOne(batch, weather);
        }
    }

    private void drawOne(Batch batch, Weather weather) {
        EntitySprite sprite = sprites.get(weather.sprite);
        if (sprite == null || !sprite.isReady()) {
            return;
        }
        String clip = ClipMap.firstAvailable(sprite, weather.clip);
        Rectangle bounds = sprite.bounds(clip);
        if (bounds == null || bounds.width <= 0f) {
            return;
        }
        float scale = SpritePlacer.toSpriteSpace(weather.widthCells * lawn.cellWidth()) / bounds.width;
        float centreY = lawn.worldY(weather.row) + lawn.cellHeight() * 0.5f;
        // A lane-wide gust is laid out in even steps across the row; a tile burst puts every copy on the
        // same square, so the step is never consulted for one.
        float step = (lawn.rightEdge() - lawn.originX()) / weather.copies;

        float previous = batch.getPackedColor();
        // The effect's own colour multiplied into whatever the batch is already carrying, so a tinted
        // effect stays tinted and an untinted one is drawn exactly as authored.
        Color batchTint = batch.getColor();
        Color own = weather.tint;
        batch.setColor(batchTint.r * own.r, batchTint.g * own.g, batchTint.b * own.b,
                batchTint.a * own.a * alphaOf(weather));

        // Wrapped rather than clamped: both clips are far shorter than the effect lasts, and a clamped
        // sample would hold the last frame -- a storm that stops mid-gale and then vanishes.
        float cycle = sprite.clipDuration(clip);
        for (int copy = 0; copy < weather.copies; copy++) {
            float centreX = weather.col >= 0
                    ? lawn.centerX(weather.col)
                    : lawn.originX() + step * (copy + 0.5f);
            float age = Math.max(0f, weather.age - copy * COPY_LAG_SECONDS);
            float elapsed = age + weather.clipPhase;
            elapsed = cycle > 0f ? elapsed % cycle : elapsed;

            Matrix4 previousTransform = batch.getTransformMatrix().cpy();
            batch.setTransformMatrix(new Matrix4(previousTransform)
                    .translate(SpritePlacer.toSpriteSpace(centreX),
                            SpritePlacer.toSpriteSpace(centreY), 0f)
                    .scale(scale, scale, 1f));
            sprite.draw(batch, clip, ClipMap.sample(sprite, clip, elapsed),
                    0f, bounds.y + bounds.height / 2f, true);
            batch.setTransformMatrix(previousTransform);
        }

        batch.setPackedColor(previous);
    }

    private static float alphaOf(Weather weather) {
        float phase = weather.age / Math.max(0.01f, weather.lifetime);
        float ramp = Math.min(phase, 1f - phase) / FADE;
        return PEAK_ALPHA * Math.min(1f, Math.max(0f, ramp));
    }
}
