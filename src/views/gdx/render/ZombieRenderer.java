package views.gdx.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import models.entities.zombies.Zombie;
import views.gdx.bridge.EntityInterpolator;
import views.gdx.map.LawnGeometry;
import views.gdx.sprite.ArmorVisibility;
import views.gdx.sprite.ClipMap;
import views.gdx.sprite.EntitySprite;
import views.gdx.sprite.SpriteRegistry;

import java.util.Map;

// Draws the zombies of one lane.
//
// Two things make this more than "look up sprite, draw at x":
//
//  * Position must be interpolated. The model moves a zombie ~2.2 world px per tick at 10 Hz; drawn
//    straight from the model it visibly stutters at 60 fps.
//  * A zombie's lane is interpolated too. A lane switch is an instant reassignment in the model, so
//    blending it turns a teleport into a hop across the row boundary.
public final class ZombieRenderer {

    // Status tints. The spec only requires statuses be "visually distinct, at least by changing the
    // zombie's colour"; frozen and chilled get the blues, hypnotised gets the purple it has in the
    // original. Butter is handled as a real part by ArmorVisibility instead of a tint.
    private static final Color FROZEN = new Color(0.55f, 0.80f, 1f, 1f);
    private static final Color CHILLED = new Color(0.75f, 0.88f, 1f, 1f);
    private static final Color HYPNOTISED = new Color(0.85f, 0.55f, 1f, 1f);
    // Submerged zombies are under the water line and only partly visible.
    private static final Color SUBMERGED = new Color(1f, 1f, 1f, 0.45f);

    private final SpriteRegistry sprites;
    private final LawnGeometry lawn;
    private final EntityInterpolator interpolator;
    private final AnimationClocks clocks;

    // Watches health frame to frame; a drop is a hit. See DamageFlash.
    private final DamageFlash flashes = new DamageFlash();

    public ZombieRenderer(SpriteRegistry sprites, LawnGeometry lawn, EntityInterpolator interpolator,
                          AnimationClocks clocks) {
        this.sprites = sprites;
        this.lawn = lawn;
        this.interpolator = interpolator;
        this.clocks = clocks;
    }

    public void draw(Batch batch, Zombie zombie, float delta, float alpha) {
        EntitySprite sprite = sprites.get(zombie.getAlias());
        String clip = ClipMap.forZombie(sprite, zombie);
        // Per zombie, restarted on clip change: otherwise the whole horde steps in unison and a
        // zombie that stops to bite starts its "eat" animation halfway through.
        // A frozen zombie's animation stops dead. The model already holds its x still, but the walk
        // clip kept playing -- so it marched on the spot and read as "the freeze does nothing". Passing
        // 0 here holds the pose instead; the clock is still touched so AnimationClocks does not sweep
        // the entry and restart the walk from frame 0 when it thaws.
        float animationDelta = zombie.getState().isFrozen() ? 0f : delta;
        float stateTime = ClipMap.sample(sprite, clip, clocks.advance(zombie, clip, animationDelta));

        float modelX = (float) zombie.getMovement().getPositionX();
        int modelLane = zombie.getMovement().getPositionY();

        float x = lawn.worldX(interpolator.x(zombie, modelX, alpha));
        float lane = interpolator.lane(zombie, modelLane, alpha);
        float footY = laneFootY(lane);

        Map<String, Boolean> parts = ArmorVisibility.forZombie(zombie, sprite);

        Color previous = batch.getColor().cpy();
        batch.setColor(tintFor(zombie));

        // Zombies walk right-to-left, so they face LEFT -- except a hypnotised one, which has turned
        // around and is walking back the other way for the player.
        boolean faceRight = zombie.getState().isHypnotized();
        SpritePlacer.drawStanding(batch, sprite, clip, stateTime, x, footY, faceRight, parts);

        // Hit flash: the same frame again, additively, so the zombie lights up white. Total HP, not the
        // body's -- a cone or a bucket absorbing a pea is still a hit, and the zombie should react.
        float flash = zombie.getHealth() == null ? 0f
                : flashes.intensity(zombie, zombie.getHealth().getTotalHP(), delta);
        if (flash > 0f) {
            SpritePlacer.beginAdditive(batch);
            batch.setColor(flash, flash, flash, 1f);
            SpritePlacer.drawStanding(batch, sprite, clip, stateTime, x, footY, faceRight, parts);
            SpritePlacer.endAdditive(batch);
        }

        batch.setColor(previous);
    }

    // Called once per frame by GameRenderer: drops entities that were not drawn.
    void sweepFlashes() {
        flashes.sweep();
    }

    // Fractional lane -> foot line, so a lane switch slides instead of snapping.
    private float laneFootY(float lane) {
        int base = (int) Math.floor(lane);
        float frac = lane - base;
        float low = lawn.worldY(base);
        float high = lawn.worldY(Math.min(base + 1, utils.Constants.BOARD_ROWS - 1));
        return low + (high - low) * frac + lawn.cellHeight() * SpritePlacer.FOOT_INSET;
    }

    private static Color tintFor(Zombie zombie) {
        if (zombie.getState().isSubmerged()) {
            return SUBMERGED;
        }
        if (zombie.getState().isFrozen()) {
            return FROZEN;
        }
        if (zombie.getState().isHypnotized()) {
            return HYPNOTISED;
        }
        if (zombie.getState().isChilled()) {
            return CHILLED;
        }
        return Color.WHITE;
    }
}
