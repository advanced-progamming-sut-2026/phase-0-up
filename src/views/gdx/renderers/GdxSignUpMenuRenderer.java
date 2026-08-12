package views.gdx.renderers;

import utils.Result;
import views.gdx.core.ToastSink;
import views.renderers.MenuRenderer.SignUpMenuRenderer;

// Registration.
public final class GdxSignUpMenuRenderer implements SignUpMenuRenderer {

    private final ToastSink toasts;

    public GdxSignUpMenuRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void register(Result result) {
        toasts.show(result);
    }

    @Override
    public void showSecurityQuestions() {
        // The terminal prints the list because the player has to type a number back. RegisterScreen
        // (T4.1) puts the same list in a dropdown, so there is nothing to announce.
    }
}
