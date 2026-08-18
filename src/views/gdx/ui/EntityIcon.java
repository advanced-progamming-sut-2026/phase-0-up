package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;

// A plant or a zombie drawn small, inside a UI widget.
//
// The art is skeletal and authored at lawn scale -- a Peashooter's idle box is about 100x150 PAM units,
// several times a seed card, and a Gargantuar's is far larger still. There is no smaller version and no
// static thumbnail anywhere in the asset dump, so the same animation is drawn through a scale-to-fit
// transform.
//
// A transform rather than a scaled draw call: libPVZ's scaled overloads take a scale but not a
// visibility map, and doing the scaling here keeps one code path for every icon regardless.
//
// Zombies use this as well as plants, which is why the tint exists -- the almanac draws a zombie the
// player has never met as a black silhouette, and there is no separate silhouette art either.
public class EntityIcon extends Actor {

    // Leaves a little air around the art so it does not touch the widget's edges.
    private static final float FIT = 0.88f;

    private final EntitySprite sprite;
    private final String clip;
    private float stateTime;

    // Re-measured whenever the visible part set changes -- see measure().
    private Rectangle bounds;

    // Multiplied into the batch colour for the duration of the draw. Null draws the art as authored.
    private Color tint;

    // Which optional parts to switch on -- a zombie's cone, bucket or brick. Null draws the animation's
    // default set, which for the shared zombie body means a bare head.
    private java.util.Map<String, Boolean> parts;

    public EntityIcon(EntitySprite sprite) {
        this.sprite = sprite;
        // Not ClipMap.firstAvailable: the staged plants have no plain "idle" and would draw nothing.
        this.clip = views.gdx.sprite.PlantStages.restingClip(sprite);
        measure();
    }

    // The box the art is fitted to, counting only what is actually drawn.
    //
    // bounds() would include every switched-off part, and on the shared zombie body that means the tallest
    // hat it could wear: a bare Browncoat measured 169x250 against a true 131x197, so every zombie card
    // drew its subject at four fifths of the size it had room for, sitting low under a helmet's worth of
    // empty space. Plants have no toggleable parts, so for them this is exactly bounds() and costs nothing.
    private void measure() {
        this.bounds = sprite.visibleBounds(clip,
                views.gdx.sprite.ArmorVisibility.hiddenParts(sprite, parts));
    }

    // Flattens the icon to one colour, for a zombie the player has not discovered. Alpha is honoured,
    // so a silhouette can be softened without a second drawable.
    public EntityIcon tinted(Color tint) {
        this.tint = tint;
        return this;
    }

    // Switches optional parts on. Conehead, Buckethead and Brick share one animation with all three hats
    // in it, so this is the only thing that tells them apart -- see ArmorVisibility.
    public EntityIcon showing(java.util.Map<String, Boolean> parts) {
        this.parts = parts;
        measure();   // the hat changes the box, so the fit has to be recomputed
        return this;
    }

    // Whether there is anything to draw at all.
    //
    // A handful of entities are simply not in the asset dump -- Rotobaga, Cat-tail, Sun-shroom -- and
    // EntitySprite's still-image fallback has nothing to fall back TO for them, so their bounds come
    // back empty. Callers use this to put a placeholder in the widget instead of a blank hole; without
    // it an almanac page for a missing plant looks like a rendering bug rather than a gap in the dump.
    public boolean hasArt() {
        return bounds != null && bounds.width > 0f && bounds.height > 0f;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        // Cards idle-animate in the original, and it costs nothing: the clock is per icon, so a grid
        // of forty does not step in unison.
        stateTime += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (bounds == null || bounds.width <= 0f || bounds.height <= 0f) {
            return;
        }
        float scale = Math.min(getWidth() / bounds.width, getHeight() / bounds.height) * FIT;

        Color previousColor = batch.getColor().cpy();
        if (tint != null) {
            batch.setColor(tint.r, tint.g, tint.b, tint.a * parentAlpha);
        }
        Matrix4 previous = batch.getTransformMatrix().cpy();
        Matrix4 scaled = new Matrix4(previous)
                .translate(getX() + getWidth() / 2f, getY() + getHeight() / 2f, 0f)
                .scale(scale, scale, 1f);

        // Changing the transform flushes the batch, so this is done once per icon rather than per part.
        batch.setTransformMatrix(scaled);
        // Same y-down correction as SpritePlacer.drawCentred: libPVZ reports bounds in the .PAM's own
        // Flash-style coordinates, where the art hangs BELOW the origin.
        sprite.draw(batch, clip, ClipMap.sample(sprite, clip, stateTime),
                0f, bounds.y + bounds.height / 2f, true, parts);
        batch.setTransformMatrix(previous);
        if (tint != null) {
            batch.setColor(previousColor);
        }
    }
}
