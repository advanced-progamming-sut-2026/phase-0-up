package views.renderers.MenuRenderer;

import models.user.User;
import utils.Result;
import views.OutputHandler;

public class LoginMenuRenderer {
    public void successOfLoggingIn(Result result) {
        OutputHandler.showMessage(result.message());
    }

    public void forgetPasswordRender(Result result){
        OutputHandler.showMessage(result.message());
    }

    public void showSecurityQuestion(User user) {
        OutputHandler.showMessage("Prove it's really you -- answer your security question:");
        OutputHandler.showMessage(stripNumbering(user.getSecurityQuestion()));
        OutputHandler.showMessage("Reply with: answer -a <your answer>");
    }

    // Drops the "1. " that the stored question text carries. The old blind substring(3) assumed every
    // question was at least three characters long and numbered exactly that way.
    private String stripNumbering(String question) {
        if (question == null) {
            return "";
        }
        return question.replaceFirst("^\\s*\\d+\\.\\s*", "");
    }

}
