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

    public ZombieRenderer(SpriteRegistry sprites, LawnGeometry lawn, EntityInterpolator interpolator) {
        this.sprites = sprites;
        this.lawn = lawn;
        this.interpolator = interpolator;
    }

    public void draw(Batch batch, Zombie zombie, float stateTime, float alpha) {
        EntitySprite sprite = sprites.get(zombie.getAlias());
        String clip = ClipMap.forZombie(sprite, zombie);

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

        batch.setColor(previous);
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
