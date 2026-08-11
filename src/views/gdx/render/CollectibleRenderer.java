package views.gdx.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import models.entities.collectibles.Sun;
import models.entities.collectibles.SunType;
import models.game.GameSession;
import views.gdx.bridge.EntityInterpolator;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

// Draws suns.
//
// The SUN animation happens to carry its own colour variants as clips -- "animation" for the normal
// one, "red" and "blue" alternates -- so the three sun types the spec asks to look different are a
// clip choice rather than three sprites.
//
// Suns are drawn from Sun.getCurrentY() rather than Entity.getY(). getY() is the rounded lane, which
// would make a falling sun drop one whole row at a time instead of descending.
public final class CollectibleRenderer {

    private static final String SUN_SPRITE = "SUN";

    // How far above the lawn a sky sun starts, in cell heights. The model drops it from the top ROW,
    // which on screen is barely above the board and reads as the sun blinking into existence rather
    // than falling from the sky. Purely visual: the landing tile and the moment it lands stay the
    // model's, the descent is simply re-mapped onto a longer path.
    private static final float SKY_DROP_CELLS = 4.5f;

    // How long that drop takes. Fixed distance + fixed duration = the SAME speed for every sun,
    // whichever row it is heading for.
    //
    // Driving the descent off the model's own y instead made row 0 snap to the ground: a sun's
    // currentY climbs to targetY, and the top row's target is ~0, so there was no distance to fall
    // through and the normalised progress went straight to 1.
    //
    // Taken FROM the model's own constant rather than tuned by eye. A hand-picked 2.6s against the
    // model's 5s meant the sun touched down on screen less than halfway through its real descent, then
    // sat motionless for the remaining 2.4s until the model agreed it had landed. Reading the constant
    // means the two cannot drift apart again if the fall is ever retuned.
    // One tick short, deliberately. The model starts descending on the tick that creates the sun,
    // while this clock starts on the frame that first draws it, so the model reaches the ground very
    // slightly first. Landing a tick early means the sun is already resting at exactly restY when the
    // hand-off happens; landing late would leave it a few pixels high and snap it down -- the same
    // defect this fixes, just smaller. A tenth of a second stationary is not visible.
    private static final float FALL_SECONDS =
            utils.Constants.SUN_FALL_DURATION_SECONDS - 1f / utils.Constants.TICKS_PER_SECOND;

    // A plant-made sun is TOSSED, not dropped: it hops up out of the plant and settles beside it.
    // The model has no arc for this -- it places the sun on the plant's tile and leaves it there -- so
    // the hop is generated here, exactly like the lobbed-projectile arc.
    //
    // The arc is a lerp from the plant to the sun's resting place PLUS a parabola that is zero at both
    // ends, so it arrives exactly where the model put it. Offsetting the sun and letting the offset
    // stop at the end of the toss is what made it jump sideways as it landed: at t=1 it was still most
    // of a cell off, and the next frame snapped it back.
    private static final float TOSS_SECONDS = 0.75f;
    private static final float TOSS_HEIGHT_CELLS = 0.85f;
    // How far above the tile centre the sun is born, so it comes out of the flower's head rather than
    // out of the dirt at its feet.
    private static final float TOSS_ORIGIN_LIFT_CELLS = 0.35f;

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final EntityInterpolator interpolator;
    private final AnimationClocks clocks;

    public CollectibleRenderer(SpriteRegistry sprites, LawnGeometry lawn,
                               EntityInterpolator interpolator, AnimationClocks clocks) {
        this.sprites = sprites;
        this.lawn = lawn;
        this.interpolator = interpolator;
        this.clocks = clocks;
    }

    public void draw(Batch batch, GameSession session, float delta, float alpha) {
        EntitySprite sprite = sprites.get(SUN_SPRITE);
        fallTime.keySet().retainAll(new java.util.HashSet<>(session.getActiveSuns()));

        // GameSession.getActiveSuns(), NOT GameMap.getActiveCollectibles(). The map's list exists but
        // SunSystem never puts suns in it, so reading it renders nothing at all -- which is exactly
        // what happened until a per-second census showed suns=0 while the event log said they were
        // falling.
        for (Sun sun : new java.util.ArrayList<>(session.getActiveSuns())) {
            if (sun.isRemovable()) {
                continue;   // collected or expired; it is swept at the end of the tick
            }
            float x = lawn.worldX(interpolator.x(sun, sun.getX(), alpha));

            // Seconds since this sun was first drawn. Drives the sky drop, the toss arc and the blink
            // phase -- all three want a smooth per-sun clock rather than the model's 10 Hz ticks.
            float age = fallTime.merge(sun, delta, Float::sum);

            // Where this sun comes to rest, derived from targetY and computed the SAME way whichever
            // branch draws it. A sun's resting height never changes once the model has set it, so this
            // needs neither the interpolator nor the rounded row -- and using either is what made a
            // landed sun hop:
            //
            //   * rounding targetY to a lane put the descent on the lane CENTRE, while the resting
            //     draw used the fractional lane, up to 0.6 of a cell lower. The sun finished falling
            //     and then dropped that last fraction in a single frame.
            //   * the interpolator tracks Sun.currentY, which overshoots targetY by up to one step,
            //     so even the two fractional values disagreed slightly.
            float restY = laneToWorldY((float) sun.getTargetY());

            float y;
            if (sun.isFalling()) {
                // Sky drop. Timed here rather than derived from the model's y, so every sun covers the
                // same distance in the same time and therefore falls at the same speed.
                float progress = Math.min(1f, age / FALL_SECONDS);
                float from = restY + SKY_DROP_CELLS * lawn.cellHeight();
                y = from + (restY - from) * progress;
            } else {
                // Produced by a plant: it belongs where the plant is, not up in the sky.
                y = restY;

                // Toss arc over its first moments: out of the flower, up, and down beside it.
                if (age < TOSS_SECONDS) {
                    float t = age / TOSS_SECONDS;
                    float settle = 1f - (1f - t) * (1f - t);   // quick out of the plant, easing in

                    int column = Math.max(0, Math.min((int) Math.floor(sun.getX()),
                            utils.Constants.BOARD_COLS - 1));
                    float fromX = lawn.centerX(column);
                    float fromY = restY + TOSS_ORIGIN_LIFT_CELLS * lawn.cellHeight();

                    x = fromX + (x - fromX) * settle;
                    y = fromY + (restY - fromY) * settle
                            + TOSS_HEIGHT_CELLS * lawn.cellHeight() * 4f * t * (1f - t);
                }
            }

            String clip = clipFor(sprite, sun.getType());
            float stateTime = ClipMap.sample(sprite, clip, clocks.advance(sun, clip, delta));

            // Packed rather than Color.cpy(): this runs per sun per frame and the copy would allocate.
            float previousColor = batch.getPackedColor();
            float blink = expiryBlink(sun, age);
            if (blink < 1f) {
                com.badlogic.gdx.graphics.Color tint = batch.getColor();
                batch.setColor(tint.r, tint.g, tint.b, tint.a * blink);
            }
            SpritePlacer.drawCentred(batch, sprite, clip, stateTime, x, y, true);
            batch.setPackedColor(previousColor);
        }
    }

    // Seconds each sun has been on screen. Identity-keyed, and pruned every frame so a long level
    // cannot accumulate entries.
    private final java.util.Map<Sun, Float> fallTime = new java.util.IdentityHashMap<>();

    // A sun that is about to time out flashes, so it reads as "grab me now" rather than vanishing with
    // no warning. The flash speeds up as the sun runs out -- a constant rate tells the player something
    // is wrong but not how urgent it is, and the acceleration is the part that actually communicates.
    //
    // Driven off the sun's own smooth clock rather than the remaining tick count: ticks arrive at 10 Hz
    // and a blink sampled from them strobes in visible steps.
    private static final float BLINK_LEAD_SECONDS = 3f;
    private static final float BLINK_SLOW_HZ = 2f;
    private static final float BLINK_FAST_HZ = 7f;
    // Never fully transparent -- a sun that disappears completely between flashes looks like it has
    // already gone, and the player stops reaching for it.
    private static final float BLINK_MIN_ALPHA = 0.2f;

    private static float expiryBlink(Sun sun, float age) {
        float remaining = sun.getRemainingTicks() / (float) utils.Constants.TICKS_PER_SECOND;
        if (remaining > BLINK_LEAD_SECONDS) {
            return 1f;
        }
        float urgency = 1f - Math.max(0f, remaining) / BLINK_LEAD_SECONDS;
        float hz = BLINK_SLOW_HZ + (BLINK_FAST_HZ - BLINK_SLOW_HZ) * urgency;
        float wave = 0.5f + 0.5f * (float) Math.cos(age * hz * 2 * Math.PI);
        return BLINK_MIN_ALPHA + (1f - BLINK_MIN_ALPHA) * wave;
    }

    // A sun's y is a continuous lane coordinate while it falls, so it is converted the same way a
    // fractional zombie lane is -- interpolating between the lanes it is passing through.
    private float laneToWorldY(float lane) {
        int base = (int) Math.floor(lane);
        float frac = lane - base;
        int lower = Math.max(0, Math.min(base, utils.Constants.BOARD_ROWS - 1));
        int upper = Math.max(0, Math.min(base + 1, utils.Constants.BOARD_ROWS - 1));
        float low = lawn.centerY(lower);
        float high = lawn.centerY(upper);
        return low + (high - low) * frac;
    }

    private static String clipFor(EntitySprite sprite, SunType type) {
        if (type == null) {
            return ClipMap.firstAvailable(sprite, "animation");
        }
        return switch (type) {
            // Radioactive suns are the purple ones; "blue" is the closest alternate the animation has.
            case RADIOACTIVE -> ClipMap.firstAvailable(sprite, "blue", "animation");
            // The big special sun reuses the warm variant so it reads apart from a normal drop.
            case SPECIAL -> ClipMap.firstAvailable(sprite, "red", "animation");
            case NORMAL -> ClipMap.firstAvailable(sprite, "animation");
        };
    }
}
