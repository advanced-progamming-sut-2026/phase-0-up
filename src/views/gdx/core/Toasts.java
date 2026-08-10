package views.gdx.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import utils.Result;

import static com.badlogic.gdx.scenes.scene2d.actions.Actions.delay;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn;
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
public final class Toasts implements Disposable {

    private static final float FADE_IN_SECONDS = 0.18f;
    private static final float HOLD_SECONDS = 2.6f;
    private static final float FADE_OUT_SECONDS = 0.45f;

    // Beyond this the stack becomes an unreadable wall. Older toasts are dropped first: during a busy
    // tick the model can emit a dozen events at once, and the newest is the one worth reading.
    private static final int MAX_VISIBLE = 5;

    private static final Color INFO = new Color(0.12f, 0.12f, 0.12f, 0.85f);
    private static final Color SUCCESS = new Color(0.13f, 0.42f, 0.15f, 0.88f);
    private static final Color ERROR = new Color(0.55f, 0.13f, 0.13f, 0.90f);

    private final Assets assets;
    private final Stage stage;
    private final Table column;

    public Toasts(Assets assets) {
        this.assets = assets;
        this.stage = new Stage(new FitViewport(PvZGame.VIRTUAL_WIDTH, PvZGame.VIRTUAL_HEIGHT));

        this.column = new Table();
        column.setFillParent(true);
        column.top().right().pad(16f);
        stage.addActor(column);

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
        if (message == null || message.isBlank()) {
            return;
        }
        trimToLimit();

        Label label = new Label(message, assets.skin());
        label.setAlignment(Align.left);
        label.setWrap(true);

        Table toast = new Table();
        toast.setBackground(assets.solid(background));
        toast.pad(10f, 14f, 10f, 14f);
        // Wrapping needs a width to wrap against, or a long event message runs off the screen edge.
        toast.add(label).width(360f).left();

        // Fade only. A slide would have to animate position, which a Table re-applies on every layout
        // pass -- the actor ends up displaced from its cell instead of sliding into it.
        toast.getColor().a = 0f;
        toast.addAction(sequence(
                fadeIn(FADE_IN_SECONDS),
                delay(HOLD_SECONDS),
                fadeOut(FADE_OUT_SECONDS),
                removeActor()));

        column.row().padTop(6f).right();
        column.add(toast).right();
    }

    // Table has no "remove the oldest row" operation, so the whole column is rebuilt from the toasts
    // that should survive. Cheap: MAX_VISIBLE is 5.
    private void trimToLimit() {
        if (column.getChildren().size < MAX_VISIBLE) {
            return;
        }
        Actor[] survivors = column.getChildren().begin();
        java.util.List<Actor> keep = new java.util.ArrayList<>();
        for (int i = column.getChildren().size - MAX_VISIBLE + 1; i < column.getChildren().size; i++) {
            keep.add(survivors[i]);
        }
        column.getChildren().end();

        column.clearChildren();
        for (Actor actor : keep) {
            column.row().padTop(6f).right();
            column.add(actor).right();
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
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
