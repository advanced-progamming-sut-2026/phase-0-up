package views.gdx.renderers;

import views.gdx.core.ToastSink;
import views.renderers.MenuRenderer.MainMenuRenderer;

// The main menu. Drawing the button list is MainMenuScreen's job (T4.2) -- including the News badge,
// which is a red dot on a button rather than a sentence -- so showMainMenu has nothing to say here.
public final class GdxMainMenuRenderer implements MainMenuRenderer {

    private final ToastSink toasts;

    public GdxMainMenuRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void logOutRender(boolean success) {
        if (success) {
            toasts.success("Signed out. See you on the lawn!");
        } else {
            toasts.error("You're not signed in, so there's nothing to sign out of.");
        }
    }

    @Override
    public void showMainMenu(boolean hasUnreadNews) {
        // The screen IS the main menu; it reads the unread-news flag itself to draw the badge.
    }
}
