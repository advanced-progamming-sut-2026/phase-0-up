package views.renderers.MenuRenderer;

import utils.Result;

// Registration: the outcome, and the security-question list the player picks from.
public interface SignUpMenuRenderer {
    void register(Result result);

    void showSecurityQuestions();
}
