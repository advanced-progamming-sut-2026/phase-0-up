package views.gdx.core;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;

// A layer above every screen, for things the SERVER decides to show.
//
// ## Why this exists
//
// Every screen owns input exclusively -- MenuScreen.show() calls Gdx.input.setInputProcessor(stage),
// and so does GameScreen. That is fine for anything the player initiates, because the screen they are
// looking at is the screen that asked. It does not work for a challenge invite: that arrives because
// somebody ELSE clicked something, and it has to be answerable wherever the player happens to be.
//
// Toasts already draws above every screen, but it takes no input at all -- it never needed to, and
// wiring buttons into it would put a permanently-live stage under every click in the game. So this is
// a separate stage that is EMPTY almost always. An empty Scene2D stage hits nothing and returns false
// from touchDown, so putting it first in a screen's InputMultiplexer costs nothing and changes no
// existing behaviour; the moment a dialog is on it, that dialog's scrim wins instead.
//
// T3.7's match-over banner and T3.9's reaction popups belong here too, for the same reason: the server
// decides when they appear, not the screen.
public final class ModalLayer implements Disposable {

    private final Stage stage = new Stage(
            new FitViewport(PvZGame.VIRTUAL_WIDTH, PvZGame.VIRTUAL_HEIGHT));

    public Stage stage() {
        return stage;
    }

    public boolean hasContent() {
        return stage.getActors().size > 0;
    }

    // One thing at a time. A second invite arriving while the first is open replaces it rather than
    // stacking, because two overlapping modal scrims are unreadable and the player cannot tell which
    // buttons belong to which.
    public void show(Actor actor) {
        stage.clear();
        stage.addActor(actor);
    }

    public void dismiss() {
        stage.clear();
    }

    // Drawn by PvZGame after the screen and after the toasts, so a dialog sits above both.
    public void render(float delta) {
        if (!hasContent()) {
            return;
        }
        stage.act(delta);
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
