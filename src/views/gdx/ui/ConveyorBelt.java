package views.gdx.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;

// Wall-nut Bowling's belt, actually running.
//
// It was previously a Table of stacked Images: the right art, the right size, and completely static, so
// the one thing a conveyor has to communicate -- that it is a conveyor, and more nuts are on the way --
// was the one thing it did not. That is T8.6's "conveyor animations".
//
// ## Which way it moves is decided by the cards, not by taste
//
// The belt runs UPWARD. `GameHud` lays the nuts out top-aligned in belt order, so the front of the
// queue is the top card and taking it shifts everything up while new deliveries arrive at the bottom.
// A belt scrolling the other way would be moving against its own cargo, which is worse than a belt that
// does not move at all.
//
// ## Scrolling by drawing one slat too many
//
// The slat is an atlas region, so its UVs cannot be scrolled -- shifting them would sample whatever the
// packer happened to put next to it on the page. Instead one more slat than fits is drawn, every slat
// is offset by a distance that wraps at the slat height, and the surplus is clipped away at the ends.
// That makes the motion continuous with no jump at the wrap, because the slat that leaves the top is
// exactly the slat that enters at the bottom.
final class ConveyorBelt extends Actor {

    // About one slot every three seconds. The mode delivers a nut roughly every five, and matching that
    // exactly makes the belt look stopped between deliveries -- a real conveyor runs continuously and
    // the cargo is what is intermittent.
    private static final float PIXELS_PER_SECOND = 27f;

    private final Drawable slat;
    private final float slatHeight;
    private float offset;

    private final Rectangle area = new Rectangle();
    private final Rectangle scissor = new Rectangle();

    ConveyorBelt(Drawable slat, float slatHeight) {
        this.slat = slat;
        this.slatHeight = Math.max(1f, slatHeight);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        // Wrapped rather than accumulated, so the float never grows large enough to lose precision over
        // a long game and start juddering.
        offset = (offset + PIXELS_PER_SECOND * delta) % slatHeight;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Stage stage = getStage();
        if (slat == null || stage == null || getHeight() <= 0f) {
            return;
        }
        area.set(0f, 0f, getWidth(), getHeight());
        // Into stage coordinates, because the belt is several containers deep and the scissor is a
        // screen rectangle. localToStageCoordinates on the corner, then the size, which is unscaled
        // here -- nothing in this HUD scales.
        com.badlogic.gdx.math.Vector2 corner =
                localToStageCoordinates(new com.badlogic.gdx.math.Vector2(0f, 0f));
        area.setPosition(corner.x, corner.y);
        stage.calculateScissors(area, scissor);

        // A failed push means the rectangle is off screen or degenerate; drawing unclipped in that case
        // would spill slats across the lawn.
        batch.flush();
        if (!ScissorStack.pushScissors(scissor)) {
            return;
        }
        try {
            // From one slat below the bottom to one past the top, so both ends are covered whatever the
            // offset happens to be.
            for (float y = -slatHeight + offset; y < getHeight() + slatHeight; y += slatHeight) {
                slat.draw(batch, getX(), getY() + y, getWidth(), slatHeight);
            }
        } finally {
            batch.flush();
            ScissorStack.popScissors();
        }
    }
}
