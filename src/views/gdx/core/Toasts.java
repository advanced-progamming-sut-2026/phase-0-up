package views.gdx.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import utils.Result;

import static com.badlogic.gdx.scenes.scene2d.actions.Actions.delay;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeOut;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.removeActor;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence;

// Transient on-screen notifications, and the reason the GUI needs almost no new error-handling code.
//
// Every one of the 57 Commands already reports through a renderer, and the model already narrates
// gameplay through GameSession.reportEvent -> drainEvents(), both carrying utils.Result. Those two
// streams land here verbatim: the phase-2 spec asks for exactly this ("it is recommended to use
// Toast/Temporary notifications for displaying errors across all menus").
//
// This owns its own Stage so it can be drawn on top of any screen -- including the game board, which
// uses a different camera -- without a screen having to cooperate.
public final class Toasts implements ToastSink, Disposable {

    // The pop-in. swingOut overshoots and settles, which is what makes a notification feel like it
    // ARRIVED rather than like it was switched on.
    private static final float POP_SECONDS = 0.4f;
    private static final float HOLD_SECONDS = 2.5f;
    private static final float FADE_OUT_SECONDS = 0.45f;
    // The alert's heartbeat: a fifth of a scale, four seconds a cycle. Big enough to catch the eye at
    // the edge of vision, small enough not to be read as the text itself changing size.
    private static final float PULSE_SCALE = 1.05f;
    private static final float PULSE_SECONDS = 0.55f;

    // Beyond this the stack becomes an unreadable wall. Older toasts are dropped first: during a busy
    // tick the model can emit a dozen events at once, and the newest is the one worth reading.
    private static final int MAX_VISIBLE = 5;

    private static final Color INFO = new Color(0.12f, 0.12f, 0.12f, 0.85f);
    private static final Color SUCCESS = new Color(0.13f, 0.42f, 0.15f, 0.88f);
    private static final Color ERROR = new Color(0.55f, 0.13f, 0.13f, 0.90f);
    // Deeper and more saturated than the plain error red: an alert is drawn over the middle of the
    // lawn, where it competes with background art rather than with a dark menu.
    // A wave warning is TEXT, not a box. It is drawn across the middle of the lawn, where a panel
    // would hide the very thing the player is being warned about -- so it wears the skin's outlined
    // face in the game's own danger red and nothing else. The outline is what lets it survive being
    // drawn over sand, ice or a Gargantuar.
    private static final Color ALERT_TEXT = new Color(1f, 0.20f, 0.16f, 1f);
    private static final Color REWARD_TEXT = new Color(1f, 0.90f, 0.52f, 1f);
    private static final Color ERROR_TEXT = new Color(1f, 0.82f, 0.78f, 1f);

    private static final float TOAST_WIDTH = 360f;
    private static final float ALERT_WIDTH = 470f;
    // How far a toast travels while it fades. Small: this is a settle, not a fly-in, and a long slide
    // reads as the notification still arriving when it is already leaving.
    private static final float DRIFT = 18f;

    private final Assets assets;
    private final views.gdx.ui.UiArt art;
    private final Stage stage;
    private final Table column;
    private final Table errors;
    private final Table alerts;

    public Toasts(Assets assets) {
        this.assets = assets;
        this.art = new views.gdx.ui.UiArt(assets);
        this.stage = new Stage(new FitViewport(PvZGame.VIRTUAL_WIDTH, PvZGame.VIRTUAL_HEIGHT));

        // Three lanes, because a notification's URGENCY is what decides where the eye should find it.
        //
        // Errors go top-centre: a refusal answers something the player just did, and the middle of the
        // top edge is where they are already looking after a click. Alerts go centre-screen, over the
        // board, because a wave is the one thing worth taking the lawn for. Rewards keep the corner --
        // pleasant, not urgent, and never in the way of the fight.
        this.column = lane(Align.topRight);
        this.errors = lane(Align.top);
        this.alerts = lane(Align.center);
        stage.addActor(column);
        stage.addActor(alerts);
        stage.addActor(errors);

        // -Dpvz.uiDebug=1 outlines every Scene2D cell and widget. Indispensable when a Table lays out
        // somewhere unexpected, because it shows the cell bounds rather than just the drawn pixels.
        if (DebugFlags.UI_DEBUG) {
            stage.setDebugAll(true);
        }
    }

    // Routes a Result by its own success flag, so callers never have to branch. This is the method the
    // Gdx*Renderer implementations will call for practically everything.
    public void show(Result result) {
        if (result == null || result.message() == null || result.message().isBlank()) {
            return;
        }
        push(result.message(), result.success() ? SUCCESS : ERROR);
    }

    // The same Result, filtered and themed by what KIND of thing it is. In-game output comes through
    // here rather than through show(): on the lawn the model narrates everything it draws, and the
    // screen is the one place that has to choose. See ToastPolicy.
    @Override
    public void showInGame(Result result) {
        if (result == null || result.message() == null || result.message().isBlank()) {
            return;
        }
        switch (views.gdx.ui.ToastPolicy.classify(result.message(), result.success())) {
            case ERROR -> raise(errors, result.message(), ERROR_TEXT, TOAST_WIDTH, Align.center,
                    true, false);
            case ALERT -> raise(alerts, result.message(), ALERT_TEXT, ALERT_WIDTH, Align.center,
                    false, true);
            case REWARD -> raise(column, result.message(), REWARD_TEXT, TOAST_WIDTH, Align.left,
                    true, false);
            default -> { }
        }
    }

    public void info(String message) {
        push(message, INFO);
    }

    public void success(String message) {
        push(message, SUCCESS);
    }

    public void error(String message) {
        push(message, ERROR);
    }

    private void push(String message, Color background) {
        raise(column, message, background, TOAST_WIDTH, Align.left, true, false);
    }

    // One toast, in one lane.
    //
    // `framed` puts it on the game's own HUD board -- the same 3-slice the sun counter and the cheat
    // panel stand on, which is authored wide and short and is therefore exactly a toast's shape.
    // `pulsing` is the wave treatment: no board at all, and a heartbeat.
    private void raise(Table lane, String message, Color textColour, float width, int textAlign,
                       boolean framed, boolean pulsing) {
        if (message == null || message.isBlank()) {
            return;
        }
        trimToLimit(lane);

        // The skin's OUTLINED face, always. A toast is drawn over gameplay by definition, and plain
        // text is legible against one background and invisible against the next -- the outline is what
        // makes it readable over sand, ice, water and a zombie alike, which is the drop shadow the
        // brief asks for done with art the game already ships.
        Label label = views.gdx.ui.MenuStyles.label(assets.skin(), message,
                views.gdx.ui.MenuStyles.HEADING);
        label.setAlignment(textAlign);
        label.setWrap(true);
        label.setColor(textColour);

        Table toast = new Table();
        if (framed) {
            toast.setBackground(art.stretchable(views.gdx.ui.UiArt.PANEL, 0.28f));
            toast.pad(10f, 20f, 12f, 20f);
        }
        toast.add(label).width(width).align(textAlign);

        // Scaling has to be drawn, and a Table draws it only with a transform; the origin has to be its
        // centre or it grows out of the bottom-left corner. Both re-taken here rather than at build
        // time, because an actor has no size until its first layout pass.
        toast.setTransform(true);
        toast.pack();
        toast.setOrigin(com.badlogic.gdx.utils.Align.center);
        toast.setScale(0f);
        toast.getColor().a = 0f;

        toast.addAction(sequence(
                Actions.parallel(
                        Actions.scaleTo(1f, 1f, POP_SECONDS, Interpolation.swingOut),
                        Actions.alpha(1f, POP_SECONDS * 0.5f)),
                delay(HOLD_SECONDS),
                fadeOut(FADE_OUT_SECONDS),
                removeActor()));

        if (pulsing) {
            // A separate, endless action rather than part of the sequence above: the sequence owns the
            // arrival and the exit, and a forever inside it would never let either finish. Added to the
            // LABEL so it cannot fight the pop-in, which is scaling the table.
            label.setOrigin(com.badlogic.gdx.utils.Align.center);
            label.addAction(Actions.forever(Actions.sequence(
                    Actions.scaleTo(PULSE_SCALE, PULSE_SCALE, PULSE_SECONDS, Interpolation.sine),
                    Actions.scaleTo(1f, 1f, PULSE_SECONDS, Interpolation.sine))));
        }

        lane.row().padTop(6f);
        lane.add(toast);
    }

    private Table lane(int align) {
        Table table = new Table();
        table.setFillParent(true);
        table.align(align);
        table.pad(18f, 16f, 16f, 16f);
        return table;
    }

    // Table has no "remove the oldest row" operation, so the whole column is rebuilt from the toasts
    // that should survive. Cheap: MAX_VISIBLE is 5.
    private void trimToLimit(Table lane) {
        if (lane.getChildren().size < MAX_VISIBLE) {
            return;
        }
        Actor[] survivors = lane.getChildren().begin();
        java.util.List<Actor> keep = new java.util.ArrayList<>();
        for (int i = lane.getChildren().size - MAX_VISIBLE + 1; i < lane.getChildren().size; i++) {
            keep.add(survivors[i]);
        }
        lane.getChildren().end();

        lane.clearChildren();
        for (Actor actor : keep) {
            lane.row().padTop(6f);
            lane.add(actor);
        }
    }

    public void render(float delta) {
        stage.act(delta);
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    // Used when leaving a level so stale gameplay messages do not linger over a menu.
    public void clear() {
        column.clearChildren();
        errors.clearChildren();
        alerts.clearChildren();
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
