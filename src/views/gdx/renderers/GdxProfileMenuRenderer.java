package views.gdx.renderers;

import views.gdx.core.ToastSink;
import views.renderers.MenuRenderer.ProfileMenuRenderer;

// Profile edits. The failure text comes from the model's validators, so the reason a change was
// refused is worded once and reads the same on both builds.
public final class GdxProfileMenuRenderer implements ProfileMenuRenderer {

    private final ToastSink toasts;

    public GdxProfileMenuRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void changeUsername(boolean success, String err) {
        report(success, "New username, same great gardener. Done!", err);
    }

    @Override
    public void changeNickname(boolean success, String err) {
        report(success, "Nickname updated -- the neighbours will be so impressed.", err);
    }

    @Override
    public void changePassword(boolean success, String err) {
        report(success, "Password changed. Locked up tighter than a Wall-nut.", err);
    }

    @Override
    public void changeEmail(boolean success, String err) {
        report(success, "Email updated. Crazy Dave will be in touch.", err);
    }

    @Override
    public void showInfo(String output) {
        // ProfileScreen (T4.3) shows the fields themselves; a toast of the same text would duplicate it.
    }

    private void report(boolean success, String done, String err) {
        if (success) {
            toasts.success(done);
        } else {
            toasts.error(err);
        }
    }
}
