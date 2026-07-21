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
        OutputHandler.showMessage(user.getSecurityQuestion().substring(3));
    }

}
