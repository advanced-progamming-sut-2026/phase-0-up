package views.renderers.MenuRenderer;

import models.user.User;
import utils.Result;

// Sign-in and password recovery.
public interface LoginMenuRenderer {
    void successOfLoggingIn(Result result);

    void forgetPasswordRender(Result result);

    void showSecurityQuestion(User user);
}
