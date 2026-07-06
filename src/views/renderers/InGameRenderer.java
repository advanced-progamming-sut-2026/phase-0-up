package views.renderers;

import utils.Result;
import views.OutputHandler;

public class InGameRenderer {
    public void render(Result result) {
        OutputHandler.showMessage(result.message());
    }

}

