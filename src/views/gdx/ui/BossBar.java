package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import models.entities.zombies.Zomboss;
import views.gdx.core.Assets;

// The Zomboss's health, in three segments.
//
// ## Why this is not the wave meter with different numbers
//
// A boss level HAS no waves -- ZombossMode authors none and WaveSystem never launches one -- so the
// meter along the bottom of the screen would read "Wave 0 / 0" and sit empty for the whole fight,
// which is the same hole Beghouled had before its match counter went in. What replaces it is not the
// same question either. The wave bar answers "how much longer"; this answers "how close is the next
// stagger", and those want different pictures: a continuous drain versus three bands the player is
// knocking out one at a time.
//
// The three bands ARE the mechanic. Every boundary the health falls through staggers the machine for a
// few seconds (see Zomboss.crossedSectionBoundary), and that window is when the player stops rebuilding
// and starts pushing damage. A bar that did not show where the boundaries were would leave the single
// most important thing about the fight to be discovered by accident.
//
// ## The art
//
// All of it is shipped, and the dump makes the design decision for us: alongside the boss meter and its
// fill there is a NOTCH -- a divider drawn across the bar -- which exists for no other reason than that
// a Zomboss bar is meant to read as segments. See UiArt.BOSS_METER_NOTCH. Fallback fills keep the bar
// legible if any id ever fails to resolve, exactly as WaveBar does.
public final class BossBar extends Actor {

    // The fill sits inside the meter's rounded cap and shadow rather than spanning the widget.
    //
    // Both insets are MEASURED off the shipped art rather than copied from WaveBar, which is where the
    // first pass went wrong. The boss meter is 313x33 and its fill stub is 22x17, so the fill occupies
    // 17/33 = 0.515 of the meter's height and each inset is the remaining (1 - 0.515) / 2. WaveBar's
    // 0.30 was tuned against a different frame and left the boss fill floating in the middle of its
    // groove. See -Dpvz.probeRegions for where these numbers come from.
    private static final float FILL_INSET_X = 0.045f;
    private static final float FILL_INSET_Y = 0.242f;

    private static final Color TRACK_FALLBACK = new Color(0.10f, 0.12f, 0.16f, 0.85f);
    private static final Color FILL_FALLBACK = new Color(0.72f, 0.14f, 0.16f, 0.95f);
    private static final Color NOTCH_FALLBACK = new Color(0.05f, 0.05f, 0.07f, 0.95f);
    // The band being worked on flashes while the machine is down, so the stagger is unmissable even if
    // the player is watching the lawn rather than the boss.
    private static final Color DIZZY_FILL = new Color(1f, 0.85f, 0.35f, 1f);

    // Same easing as the wave meter: a boss takes chip damage constantly and a bar written straight
    // from the model jitters, while one that eases reads as draining.
    private static final float GLIDE_RATE = 6f;
    private static final float GLIDE_SNAP = 0.001f;

    // How wide a notch is drawn, as a fraction of the bar's height.
    private static final float NOTCH_WIDTH = 0.22f;
    private static final float DIZZY_FLASH_HZ = 6f;

    private final Assets assets;
    private final UiArt art;

    private float target = 1f;
    private float health = 1f;
    private boolean dizzy;
    private float clock;

    public BossBar(Assets assets, UiArt art) {
        this.assets = assets;
        this.art = art;
    }

    // Reads the boss directly. Every number here is derived from its health, so there is nothing to
    // keep in step -- and a null boss (the fight has not started, or the machine is already scrap)
    // simply leaves the bar where it was rather than snapping it to zero.
    public void set(Zomboss boss) {
        if (boss == null) {
            return;
        }
        this.target = Math.max(0f, Math.min(1f, boss.healthFraction()));
        this.dizzy = boss.getState().isDizzy();
    }

    public float health() {
        return health;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        clock += delta;
        float gap = target - health;
        if (Math.abs(gap) <= GLIDE_SNAP) {
            health = target;
            return;
        }
        health += gap * Math.min(1f, delta * GLIDE_RATE);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();

        TextureRegion meter = art.region(UiArt.BOSS_METER);
        if (meter == null) {
            meter = art.region(UiArt.METER);
        }
        if (meter == null) {
            assets.solid(TRACK_FALLBACK).draw(batch, x, y, w, h);
        } else {
            batch.draw(meter, x, y, w, h);
        }

        float trackX = x + w * FILL_INSET_X;
        float trackW = w - 2f * w * FILL_INSET_X;
        float fy = y + h * FILL_INSET_Y;
        float fh = h - 2f * h * FILL_INSET_Y;

        drawFill(batch, trackX, fy, trackW * health, fh);
        drawNotches(batch, trackX, y, trackW, h);
        drawHead(batch, trackX, y, trackW, h);
    }

    // The remaining health, flashing while the machine is down.
    private void drawFill(Batch batch, float fx, float fy, float fw, float fh) {
        if (fw <= 0f) {
            return;
        }
        Color previous = batch.getColor().cpy();
        if (dizzy) {
            // A sine rather than a hard blink: a bar strobing at six hertz is a headache, and the point
            // is to draw the eye rather than to alarm.
            float pulse = 0.5f + 0.5f * (float) Math.sin(clock * DIZZY_FLASH_HZ);
            batch.setColor(previous.r * lerp(1f, DIZZY_FILL.r, pulse),
                    previous.g * lerp(1f, DIZZY_FILL.g, pulse),
                    previous.b * lerp(1f, DIZZY_FILL.b, pulse), previous.a);
        }
        TextureRegion fill = art.region(UiArt.BOSS_METER_FILL);
        if (fill == null) {
            fill = art.region(UiArt.METER_FILL);
        }
        if (fill == null) {
            assets.solid(FILL_FALLBACK).draw(batch, fx, fy, fw, fh);
        } else {
            batch.draw(fill, fx, fy, fw, fh);
        }
        batch.setColor(previous);
    }

    // The two dividers between the three bands. Two, not three: the bar's own ends are the outer
    // boundaries, and a notch drawn on top of the cap reads as damage to the meter rather than as a
    // mark on it.
    private void drawNotches(Batch batch, float trackX, float y, float trackW, float h) {
        TextureRegion notch = art.region(UiArt.BOSS_METER_NOTCH);
        // A notch is as tall as the fill it divides -- the art agrees, both stubs being 17px in a
        // 33px meter -- and as wide as its OWN aspect makes it. That last part is the whole fix: the
        // divider ships at 5x17, and drawing it square (h by h, as the zombie head is drawn) stretched
        // a five-pixel line into a thirty-unit slab, so the bar came out looking broken into three
        // pieces with a dark red post wedged between them rather than marked with two thin dividers.
        float fh = h - 2f * h * FILL_INSET_Y;
        float fy = y + h * FILL_INSET_Y;
        float width = notch == null
                ? h * NOTCH_WIDTH
                : fh * notch.getRegionWidth() / (float) notch.getRegionHeight();
        for (int i = 1; i < Zomboss.SECTIONS; i++) {
            float at = trackX + trackW * (i / (float) Zomboss.SECTIONS);
            if (notch == null) {
                assets.solid(NOTCH_FALLBACK).draw(batch, at - width / 2f, fy, width, fh);
            } else {
                batch.draw(notch, at - width / 2f, fy, width, fh);
            }
        }
    }

    // The skull riding the bar at the current health, the way the zombie head rides the wave meter --
    // and for the same reason: the end of the fill is far easier to find at a glance when something is
    // sitting on it.
    private void drawHead(Batch batch, float trackX, float y, float trackW, float h) {
        TextureRegion head = art.region(UiArt.BOSS_METER_HEAD);
        if (head == null) {
            head = art.region(UiArt.BOSS_SKULL);
        }
        if (head == null) {
            return;
        }
        // At its own aspect: the boss head ships 47x53, so a square draw squashes it. Same reason the
        // notch is drawn from its region's proportions rather than from the bar's height.
        float height = h * 1.6f;
        float width = height * head.getRegionWidth() / (float) head.getRegionHeight();
        float at = trackX + trackW * health;
        batch.draw(head, at - width / 2f, y + h * 0.5f - height / 2f, width, height);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
