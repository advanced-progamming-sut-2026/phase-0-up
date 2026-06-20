package views.renderers.MenuRenderer;

import utils.Constants;
import utils.Result;
import views.OutputHandler;

public class SignUpMenuRenderer {
    public void register(Result result) {
        OutputHandler.showMessage(result.message());
    }
    public void showSecurityQuestions(){
        OutputHandler.showMessage("Please choose your security questions");
        for (String question : Constants.SECURITY_QUESTIONS) {
            OutputHandler.showMessage(question);
        }
    }
}
