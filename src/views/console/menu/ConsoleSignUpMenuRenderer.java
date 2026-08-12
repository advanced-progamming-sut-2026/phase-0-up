package views.console.menu;

import utils.Constants;
import utils.Result;
import views.OutputHandler;
import views.renderers.MenuRenderer.SignUpMenuRenderer;

public class ConsoleSignUpMenuRenderer implements SignUpMenuRenderer {
    @Override
    public void register(Result result) {
        OutputHandler.showMessage(result.message());
    }

    @Override
    public void showSecurityQuestions(){
        OutputHandler.showMessage("Pick a security question -- in case the zombies eat your memory:");
        for (String question : Constants.SECURITY_QUESTIONS) {
            OutputHandler.showMessage(question);
        }
    }
}
