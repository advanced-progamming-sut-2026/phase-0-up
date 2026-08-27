package views.gdx.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import views.gdx.core.Assets;

// "Are you sure?" -- for the handful of actions in this game that spend the player's money.
//
// Not a Scene2D Dialog. The skin declares no WindowStyle at all (there is no window art in the dump)
// and Dialog requires one; it also brings its own show/hide machinery and wants to own a Stage. What is
// actually needed here is smaller: a dimmed layer that swallows clicks, the same framed panel every
// menu uses, and two buttons.
//
// The scrim is Touchable.enabled and consumes clicks on purpose. Without it the controls UNDERNEATH
// stay live, and the second half of an impatient double-click lands on the thing being confirmed.
// WidgetGroup, not Group: only WidgetGroup honours setFillParent, and the layer has to cover the whole
// stage or the two Tables inside it measure themselves against a zero-sized parent.
public final class ConfirmDialog extends WidgetGroup {

    private static final Color SCRIM = new Color(0f, 0f, 0f, 0.62f);
    private static final float BODY_WIDTH = 460f;
    // How far the opaque fill is tucked inside the ornate frame, so it does not show square shoulders
    // past the frame's rounded corners. Same reason and same figure as MenuPanel's.
    private static final float FILL_INSET = 18f;

    // What the second button says and does. Null means there is no second button.
    //
    // Existing callers pass a plain Cancel that only closes the dialog, which is right when the
    // decision is the player's alone. A challenge invite is not that: declining has to TELL the person
    // waiting, so the second button needs an action of its own and a word other than "Cancel".
    private record Alternative(String text, Runnable action) { }

    private ConfirmDialog(Assets assets, Skin skin, String title, String body,
                          String confirmText, Runnable onConfirm, boolean cancellable) {
        this(assets, skin, title, body, confirmText, onConfirm,
                cancellable ? new Alternative("Cancel", null) : null);
    }

    private ConfirmDialog(Assets assets, Skin skin, String title, String body,
                          String confirmText, Runnable onConfirm, Alternative alternative) {
        setFillParent(true);

        Table scrim = new Table();
        scrim.setFillParent(true);
        scrim.setBackground(assets.solid(SCRIM));
        scrim.setTouchable(Touchable.enabled);
        scrim.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // Deliberately does nothing. Dismissing on a stray background click is how a purchase
                // gets cancelled by accident; Cancel is right there and says what it does.
            }
        });
        addActor(scrim);

        Table layer = new Table();
        layer.setFillParent(true);
        layer.add(panel(skin, title, body, confirmText, onConfirm, alternative));
        addActor(layer);
    }

    private Table panel(Skin skin, String title, String body, String confirmText,
                        Runnable onConfirm, Alternative alternative) {
        Table panel = new Table();
        Drawable border = MenuStyles.drawable(skin, MenuStyles.PANEL_BORDER);
        Drawable fill = MenuStyles.panelFill(skin);
        if (border != null) {
            // The frame's middle is translucent, so the opaque fill goes underneath it as one
            // background -- a Table has room for exactly one.
            panel.setBackground(fill == null ? border : layered(fill, border));
        }
        panel.pad(26f, 44f, 26f, 44f);

        panel.add(MenuStyles.label(skin, title, MenuStyles.HEADING)).width(BODY_WIDTH)
                .padBottom(10f).row();

        Label text = MenuStyles.label(skin, body, MenuStyles.TEXT);
        text.setWrap(true);
        text.setAlignment(Align.center);
        panel.add(text).width(BODY_WIDTH).padBottom(18f).row();

        Table buttons = new Table();
        buttons.add(button(skin, confirmText, MenuStyles.BUTTON_GREEN, onConfirm))
                .width(230f).height(56f).padRight(alternative != null ? 12f : 0f);
        if (alternative != null) {
            buttons.add(button(skin, alternative.text(), MenuStyles.BUTTON_BROWN, alternative.action()))
                    .width(170f).height(56f);
        }
        panel.add(buttons).row();
        return panel;
    }

    private static Drawable layered(Drawable under, Drawable over) {
        return new BaseDrawable(over) {
            @Override
            public void draw(Batch batch, float x, float y, float width, float height) {
                under.draw(batch, x + FILL_INSET, y + FILL_INSET,
                        width - FILL_INSET * 2f, height - FILL_INSET * 2f);
                over.draw(batch, x, y, width, height);
            }
        };
    }

    private TextButton button(Skin skin, String text, String style, Runnable action) {
        TextButton button = MenuStyles.button(skin, text, style);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                remove();
                if (action != null) {
                    action.run();
                }
            }
        });
        return button;
    }

    // Opens over whatever is on the stage. It removes itself on either button, so callers never have to
    // keep a reference or remember to close it.
    public static void show(Stage stage, Assets assets, Skin skin, String title, String body,
                            String confirmText, Runnable onConfirm) {
        stage.addActor(new ConfirmDialog(assets, skin, title, body, confirmText, onConfirm, true));
    }

    // Two real choices, both of which DO something.
    //
    // Distinct from show() because there the second button is a way out -- it closes the dialog and
    // nothing else happened. A challenge invite has no such button: accepting starts a match and
    // declining tells the person waiting, so leaving it alone is not one of the options.
    public static void decide(Stage stage, Assets assets, Skin skin, String title, String body,
                              String acceptText, Runnable onAccept,
                              String declineText, Runnable onDecline) {
        stage.addActor(new ConfirmDialog(assets, skin, title, body, acceptText, onAccept,
                new Alternative(declineText, onDecline)));
    }

    // The same panel with nothing to decide: it reports something that has ALREADY happened, so there
    // is no Cancel, because there is nothing left to cancel. The greenhouse harvest uses it -- a
    // toast says what the model did, and this says it loudly enough to feel like a reward.
    public static void announce(Stage stage, Assets assets, Skin skin, String title, String body,
                                String dismissText) {
        stage.addActor(new ConfirmDialog(assets, skin, title, body, dismissText, null, false));
    }
}
