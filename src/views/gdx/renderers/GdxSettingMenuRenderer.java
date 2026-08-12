package views.gdx.renderers;

import views.gdx.core.ToastSink;
import views.renderers.MenuRenderer.SettingMenuRenderer;

// Settings.
public final class GdxSettingMenuRenderer implements SettingMenuRenderer {

    private final ToastSink toasts;

    public GdxSettingMenuRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void changeDL(boolean success, int newDL) {
        if (success) {
            toasts.success("Difficulty set to " + newDL + ". The zombies have been notified.");
        } else {
            toasts.error(newDL + " isn't a difficulty level anyone recognises.");
        }
    }
}
