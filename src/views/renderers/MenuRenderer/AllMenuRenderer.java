package views.renderers.MenuRenderer;

import controllers.engine.MenuType;
import utils.Result;

// The navigation messages every menu shares: where you are, where you just went, and what to do about
// a command nobody recognised.
public interface AllMenuRenderer {
    void showCurrentMenu(MenuType menu);

    void enterMenu(Result result);

    void menuExit(String destination);

    void applicationExit();

    void invalidCommand();
}
