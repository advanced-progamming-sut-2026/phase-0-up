package views.gdx.renderers;

import utils.Result;
import views.gdx.core.ToastSink;
import views.renderers.MenuRenderer.LoginMenuRenderer;

// Sign-in and password recovery.
public final class GdxLoginMenuRenderer implements LoginMenuRenderer {

    private final ToastSink toasts;

    public GdxLoginMenuRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void successOfLoggingIn(Result result) {
        toasts.show(result);
    }

    @Override
    public void forgetPasswordRender(Result result) {
        toasts.show(result);
    }

    @Override
    public void showSecurityQuestion(String question) {
        // No "reply with: answer -a ..." instruction: on this build the question is a labelled field on
        // ForgotPasswordScreen (T4.1), and the answer is submitted with the form.
        toasts.info(stripNumbering(question));
    }

    // Drops the "1. " that the stored question text carries.
    private String stripNumbering(String question) {
        if (question == null) {
            return "";
        }
        return question.replaceFirst("^\\s*\\d+\\.\\s*", "");
    }
}
