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

// The two things the WEATHER does to a lawn: Ancient Egypt's tornado and Frostbite's freezing wind.
//
// Driven off the model's narration, the same way ExplosionEffects is, and for the same reason: neither
// leaves anything on the board to infer it from. A tornado's whole effect is that some of the wave's
// zombies are already deep on the lawn instead of walking in from the edge -- which, without this,
// looks exactly like zombies spawning in the wrong place. The freezing wind chills every plant in a row
// and then is gone; the only trace is that a row of plants is suddenly a shade bluer.
//
// Both messages are sentences the model already emits for the terminal build, so nothing was added to
// the model to make this work.
public final class WeatherEffects {

    // WaveSystem.applyEgyptTornado announces the storm, and then emits one of these per zombie it
    // drops. The DROP line is what gets drawn: it names the lane and how many columns past the spawn
    // edge the zombie landed, which is the tile the sand is actually arriving on.
    private static final Pattern TORNADO_DROP =
            Pattern.compile("^The tornado drops .+ into lane (\\d+), (\\d+) column\\(s\\) past the edge\\.$");
    // EnvironmentSystem.applyFreezingWind, one per gusted row.
    private static final Pattern CHILL_WIND =
            Pattern.compile("^A freezing wind sweeps through row (\\d+)\\.$");

    // Egypt's sandstorm ships in the same two halves the Cherry Bomb's blast does. A storm has nothing
    // to wrap around -- it is weather, in front of everything -- so both halves go in front, together:
    // each on its own is a few thin wisps, and the pair is a wall of sand, which is what the model is
    // narrating when it drops four zombies into the middle of the lawn.
    private static final String SANDSTORM = "SANDSTORM_TOP";
    private static final String SANDSTORM_REAR = "SANDSTORM_REAR";
    private static final String SANDSTORM_CLIP = "loop";
    private static final String CHILL_WIND_SPRITE = "FROSTBITE_CHILL_WIND";
    private static final String CHILL_WIND_CLIP = "animation";

    private static final float STORM_SECONDS = 2.2f;
    private static final float GUST_SECONDS = 2.4f;

    // Both are authored as ONE gust on a 390-unit canvas, not as a backdrop, so covering a whole lane
    // means drawing several copies side by side rather than stretching one to nine cells wide -- which
    // is what the first pass did, and it thinned the art out until the wind was a couple of hairlines.
    private static final float GUST_WIDTH_CELLS = 3.6f;
    private static final int GUST_COPIES = 3;

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
    }

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final List<Weather> active = new ArrayList<>();

    public WeatherEffects(SpriteRegistry sprites, LawnGeometry lawn) {
        this.sprites = sprites;
        this.lawn = lawn;
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
            raise(CHILL_WIND_SPRITE, CHILL_WIND_CLIP, GUST_SECONDS, GUST_WIDTH_CELLS,
                    Integer.parseInt(gust.group(1)), -1, GUST_COPIES);
        }
    }

    private void raise(String sprite, String clip, float lifetime, float widthCells, int row,
                       int col, int copies) {
        Weather weather = new Weather();
        weather.sprite = sprite;
        weather.clip = clip;
        weather.lifetime = lifetime;
        weather.widthCells = widthCells;
        weather.row = row;
        weather.col = col;
        weather.copies = Math.max(1, copies);
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
        Color tint = batch.getColor();
        batch.setColor(tint.r, tint.g, tint.b, tint.a * alphaOf(weather));

        // Wrapped rather than clamped: both clips are far shorter than the effect lasts, and a clamped
        // sample would hold the last frame -- a storm that stops mid-gale and then vanishes.
        float cycle = sprite.clipDuration(clip);
        for (int copy = 0; copy < weather.copies; copy++) {
            float centreX = weather.col >= 0
                    ? lawn.centerX(weather.col)
                    : lawn.originX() + step * (copy + 0.5f);
            float age = Math.max(0f, weather.age - copy * COPY_LAG_SECONDS);
            float elapsed = cycle > 0f ? age % cycle : age;

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
