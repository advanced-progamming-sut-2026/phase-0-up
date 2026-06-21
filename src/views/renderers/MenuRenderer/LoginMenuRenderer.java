package views.renderers.MenuRenderer;

import models.user.User;
import utils.Result;
import views.OutputHandler;

public class LoginMenuRenderer {
    public void successOfLoggingIn(Result result) {
        OutputHandler.showMessage(result.message());
    }
    public void forgetPasswordRender(boolean success , User currentUser){}

}
