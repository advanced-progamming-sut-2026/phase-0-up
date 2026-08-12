package views.gdx.renderers;

import controllers.engine.MenuType;
import utils.Result;
import views.gdx.core.ToastSink;
import views.renderers.MenuRenderer.AllMenuRenderer;

// Navigation feedback.
//
// The move itself is not this class's doing: the Commands set AppSession's current menu and ScreenStack
// syncs the screen to it every frame. All that is left is telling the player what happened, which is
// what a toast is for.
public final class GdxAllMenuRenderer implements AllMenuRenderer {

    private final ToastSink toasts;

    public GdxAllMenuRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void showCurrentMenu(MenuType menu) {
        toasts.info("You are in the " + menu.getMenuName() + " menu.");
    }

    @Override
    public void enterMenu(Result result) {
        toasts.show(result);
    }

    @Override
    public void menuExit(String destination) {
        toasts.info(String.format("Back to the %s menu.", destination));
    }

    @Override
    public void applicationExit() {
        toasts.success("Progress saved. Thanks for defending the lawn -- see you next time!");
    }

    @Override
    public void invalidCommand() {
        toasts.error("That one didn't land. Try a button instead!");
    }
}
