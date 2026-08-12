package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import views.gdx.core.Assets;

// How far through the level's waves the player is, drawn with the game's own progress meter.
//
// Computed entirely view-side from two numbers the model already publishes -- the current wave and the
// level's wave count. There is no progress value in the model and there should not be: it is a way of
// looking at the waves, not a fact about them.
//
// The meter, its fill, the wave flags and the zombie head that rides along it are all shipped art
// (IMAGE_UI_HUD_INGAME_PROGRESS_METER*). A flat two-colour bar stood in here until those ids were
// found.
public final class WaveBar extends Actor {

    // The meter art has a rounded cap and a shadow, so the fill has to sit inside it rather than span
    // the whole widget. Fractions of the widget, measured against the source image.
    private static final float FILL_INSET_X = 0.045f;
    private static final float FILL_INSET_Y = 0.30f;

    private static final Color TRACK_FALLBACK = new Color(0.10f, 0.12f, 0.16f, 0.75f);
    private static final Color FILL_FALLBACK = new Color(0.85f, 0.25f, 0.22f, 0.95f);

    private final Assets assets;
    private final UiArt art;

    private float progress;
    private int waves;

    public WaveBar(Assets assets, UiArt art) {
        this.assets = assets;
        this.art = art;
    }

    public void set(float progress, int waves) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        this.waves = waves;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();

        TextureRegion meter = art.region(UiArt.METER);
        TextureRegion fill = art.region(UiArt.METER_FILL);

        if (meter == null) {
            assets.solid(TRACK_FALLBACK).draw(batch, x, y, w, h);
        } else {
            batch.draw(meter, x, y, w, h);
        }

        float fx = x + w * FILL_INSET_X;
        float fy = y + h * FILL_INSET_Y;
        float fw = (w - 2 * w * FILL_INSET_X) * progress;
        float fh = h - 2 * h * FILL_INSET_Y;
        if (progress > 0f) {
            if (fill == null) {
                assets.solid(FILL_FALLBACK).draw(batch, fx, fy, fw, fh);
            } else {
                // The fill source is a short stub meant to be tiled or stretched along the meter.
                batch.draw(fill, fx, fy, fw, fh);
            }
        }

        drawHead(batch, x, y, w, h);
    }

    // A flag per wave, plus the zombie head marking how far the horde has got.
    //
    // The flags sit ABOVE the meter rather than across it. Drawn over the bar they cut the fill into
    // notches and it read as a segmented gauge; standing on top they mark the same points without
    // interrupting the line, which is what "continuous" asked for and still shows how many waves there
    // are at a glance.
    private void drawHead(Batch batch, float x, float y, float w, float h) {
        TextureRegion flag = art.region(UiArt.METER_FLAG);
        TextureRegion head = art.region(UiArt.METER_HEAD);

        if (flag != null && waves > 0) {
            float size = h * 0.85f;
            float track = w - 2 * w * FILL_INSET_X;
            for (int wave = 1; wave <= waves; wave++) {
                float at = x + w * FILL_INSET_X + track * (wave / (float) waves);
                batch.draw(flag, at - size / 2f, y + h * 0.72f, size, size);
            }
        }

        // The head sits ON the meter at the current position -- the original's most readable cue for
        // "how much longer", far more than the fill length alone.
        if (head != null) {
            float size = h * 1.5f;
            float at = x + w * FILL_INSET_X + (w - 2 * w * FILL_INSET_X) * progress;
            batch.draw(head, at - size / 2f, y + h * 0.5f - size / 2f, size, size);
        }
    }
}
