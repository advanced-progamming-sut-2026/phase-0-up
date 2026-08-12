package controllers.commands.authentication;

import controllers.commands.Command;
import models.user.AppSession;
import models.user.User;
import utils.Result;
import utils.regex.LoginMenuRegex;
import utils.storage.DatabaseManager;
import utils.storage.PasswordHasher;
import utils.storage.SecurityAnswer;
import utils.validation.PasswordValidator;
import views.InputHandler;
import views.renderers.MenuRenderer.LoginMenuRenderer;

// Password recovery, in two flavours.
//
// The terminal flavour is a conversation: it asks the security question, waits, asks for a new
// password, waits. Those waits are blocking reads on stdin, and calling them from the graphical build
// would freeze the render thread forever -- LibGDX has one, it draws every frame on it, and it is the
// same thread a button's click listener runs on.
//
// So there is a second constructor that takes every answer up front. The rules below do not change
// between the two: the same regex, the same validators, the same refusal sentences. The only
// difference is where the answers came from -- a prompt, or a form the player already filled in.
public class ForgetPasswordCommand implements Command {
    private final String username;
    private final String email;
    private final AppSession appSession;
    private final LoginMenuRenderer loginMenuRenderer;

    // Null in the interactive flavour, which is exactly what "ask the player" is encoded as. Non-null
    // means the answers are already in hand and stdin must never be touched.
    private final String suppliedSecurityAnswer;
    private final String suppliedNewPassword;
    private final boolean interactive;

    public ForgetPasswordCommand(String input, AppSession appSession, LoginMenuRenderer loginMenuRenderer) {
        this.username = LoginMenuRegex.FORGET_PASSWORD.getGroup(input, "username");
        this.email = LoginMenuRegex.FORGET_PASSWORD.getGroup(input, "email");
        this.appSession = appSession;
        this.loginMenuRenderer = loginMenuRenderer;
        this.suppliedSecurityAnswer = null;
        this.suppliedNewPassword = null;
        this.interactive = true;
    }

    // Non-interactive: for any front end that collects the whole form before submitting it. Nothing on
    // this path reads stdin, so it is safe to call from a render thread.
    public ForgetPasswordCommand(String username, String email, String securityAnswer,
                                 String newPassword, AppSession appSession,
                                 LoginMenuRenderer loginMenuRenderer) {
        this.username = username;
        this.email = email;
        this.appSession = appSession;
        this.loginMenuRenderer = loginMenuRenderer;
        this.suppliedSecurityAnswer = securityAnswer == null ? "" : securityAnswer;
        this.suppliedNewPassword = newPassword == null ? "" : newPassword;
        this.interactive = false;
    }

    @Override
    public void execute() {
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        User user = databaseManager.findUser(username);

        if (user == null) {
            loginMenuRenderer.forgetPasswordRender(new Result(false, "User not found!"));
            return;
        }
        if (!user.getEmail().equalsIgnoreCase(email)) {
            loginMenuRenderer.forgetPasswordRender(new Result(false, "That email doesn't match this gardener."));
            return;
        }

        if (!processSecurityAnswer(user)) {
            return;
        }

        processPasswordReset(user);
        databaseManager.saveAll();
    }

    private boolean processSecurityAnswer(User user) {
        String answer = obtainSecurityAnswer(user);
        if (answer == null) {   // EOF, malformed, or cancelled -- already reported where it happened
            return false;
        }

        if (SecurityAnswer.isBlank(answer)) {
            loginMenuRenderer.forgetPasswordRender(new Result(false, "An empty answer won't fool anyone."));
            return false;
        }

        if (!SecurityAnswer.matches(answer, user.getSecurityAnswerHash())) {
            loginMenuRenderer.forgetPasswordRender(new Result(false, "Invalid answer!"));
            return false;
        }

        // The account was registered before answers were normalized, so its digest is of the raw
        // answer. Re-store it in canonical form now that we've seen a correct answer -- the old digest
        // would keep working only for a byte-identical retype.
        if (SecurityAnswer.wasLegacyMatch(answer, user.getSecurityAnswerHash())) {
            user.setSecurityAnswerHash(SecurityAnswer.hash(answer));
            DatabaseManager.getInstance().saveAll();
        }
        return true;
    }

    // The answer itself, however this front end gets one. Returns null when there is no answer to
    // check, having already said why.
    private String obtainSecurityAnswer(User user) {
        if (!interactive) {
            return suppliedSecurityAnswer;   // the screen showed the question and collected the answer
        }

        loginMenuRenderer.showSecurityQuestion(user);
        String input = InputHandler.readLine();
        if (input == null) {   // EOF: abort the reset
            return null;
        }
        input = input.trim();

        if (!LoginMenuRegex.ANSWER_SECURITY.matches(input)) {
            loginMenuRenderer.forgetPasswordRender(new Result(false, "That answer isn't in the right format."));
            return null;
        }
        return LoginMenuRegex.ANSWER_SECURITY.getGroup(input, "answer");
    }

    private void processPasswordReset(User user) {
        String newPassword = obtainNewPassword();
        if (newPassword == null) {   // EOF: abort the reset
            return;
        }

        Result validationResult = new PasswordValidator().validate(newPassword);
        if (!validationResult.success()) {
            loginMenuRenderer.forgetPasswordRender(validationResult);
            return;
        }

        user.changePassword(PasswordHasher.hash(newPassword));
        DatabaseManager.getInstance().saveAll();

        loginMenuRenderer.forgetPasswordRender(new Result(true, "Password changed successfully!"));
    }

    private String obtainNewPassword() {
        if (!interactive) {
            return suppliedNewPassword;
        }
        loginMenuRenderer.forgetPasswordRender(new Result(true, "Enter new password:"));
        String newPassword = InputHandler.readLine();
        return newPassword == null ? null : newPassword.trim();
    }
}
