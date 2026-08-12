package views.console;

import utils.Result;
import views.OutputHandler;
import views.renderers.InGameRenderer;

// Prints an in-game command's result to stdout. The terminal build's InGameRenderer.
public class ConsoleInGameRenderer implements InGameRenderer {
    @Override
    public void render(Result result) {
        OutputHandler.showMessage(result.message());
    }
}
