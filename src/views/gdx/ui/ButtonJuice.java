package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;

// What makes a widget feel like a button: it swells and brightens under the pointer, sinks when
// pressed, and springs back when released.
//
// Attached by MenuStyles.button, so every button on every menu screen has it without a screen asking.
// That is deliberate -- consistency here is the whole point, and seven screens each wiring their own
// hover is how a UI ends up with six slightly different ones.
//
// Carries no action. What a button DOES stays with the screen that built it, as a ChangeListener; this
// only decides how pressing it feels.
public final class ButtonJuice extends ClickListener {

    // Widgets rest slightly dimmed so hovering can brighten them. Tint multiplies, so a button already
    // at full white has nowhere brighter to go -- "brighten on hover" has to be built as "dim at rest".
    public static final Color REST = new Color(0.84f, 0.84f, 0.84f, 1f);
    public static final Color HOVER = Color.WHITE;

    private static final float HOVER_SCALE = 1.05f;
    private static final float PRESS_SCALE = 0.95f;
    private static final float HOVER_SECONDS = 0.12f;

    private final Actor target;

    private ButtonJuice(Actor target) {
        this.target = target;
    }

    // Wires a widget for hover and press feedback. Anything clickable can take it -- a TextButton, or
    // the seed cards on the selection screen, which are Tables.
    public static <T extends Actor> T applyTo(T actor) {
        // Without transform the scale is simply not drawn; without a centred origin it grows out of
        // the bottom-left corner.
        if (actor instanceof com.badlogic.gdx.scenes.scene2d.Group group) {
            group.setTransform(true);
        }
        actor.setColor(REST);
        actor.addListener(new ButtonJuice(actor));
        return actor;
    }

    // Restarted from scratch each time. Without clearActions a fast in-out-in leaves two tweens
    // fighting over the same scale and the widget settles somewhere between the two.
    private void animate(float scale, float seconds, Interpolation ease, Color tint) {
        // Re-taken every time rather than set once at build. A widget has no size until its first
        // layout pass, so an origin set in the constructor is (0, 0).
        target.setOrigin(Align.center);
        target.clearActions();
        target.addAction(Actions.parallel(
                Actions.scaleTo(scale, scale, seconds, ease),
                Actions.color(tint, HOVER_SECONDS)));
    }

    @Override
    public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
        // Only a real mouse move. The synthetic enter that fires as a finger or a held click crosses a
        // child actor arrives with pointer >= 0, and would retrigger the animation mid-press.
        if (pointer == -1) {
            animate(HOVER_SCALE, HOVER_SECONDS, Interpolation.swingOut, HOVER);
        }
    }

    @Override
    public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
        if (pointer == -1) {
            animate(1f, HOVER_SECONDS, Interpolation.sine, REST);
        }
    }

    @Override
    public boolean touchDown(InputEvent event, float x, float y, int pointer, int mouseButton) {
        animate(PRESS_SCALE, 0.06f, Interpolation.sine, HOVER);
        return super.touchDown(event, x, y, pointer, mouseButton);
    }

    @Override
    public void touchUp(InputEvent event, float x, float y, int pointer, int mouseButton) {
        super.touchUp(event, x, y, pointer, mouseButton);
        // Overshoots its resting size before settling, which is what sells the press as physical rather
        // than as a state change. Where it settles depends on whether the pointer is still on the
        // widget -- releasing after dragging off should not leave it stuck enlarged.
        boolean stillOver = isOver();
        animate(stillOver ? HOVER_SCALE : 1f, 0.18f, Interpolation.swingOut,
                stillOver ? HOVER : REST);
    }
}
