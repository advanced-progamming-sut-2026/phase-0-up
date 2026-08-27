package views.renderers.MenuRenderer;

import utils.Result;

// Sign-in and password recovery.
public interface LoginMenuRenderer {
    void successOfLoggingIn(Result result);

    void forgetPasswordRender(Result result);

    // The question text, not the account it belongs to.
    //
    // Both implementations only ever read User.getSecurityQuestion() off the account they were handed,
    // and passing the whole User meant the recovery flow had to have one -- which, once the roster
    // lives on a server, means asking it to hand over an account nobody has authenticated as. The
    // question is the only part a view ever needed.
    void showSecurityQuestion(String question);
}
