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

public class ForgetPasswordCommand implements Command {
    private final String username;
    private final String email;
    private final AppSession appSession;
    private final LoginMenuRenderer loginMenuRenderer;

    public ForgetPasswordCommand(String input, AppSession appSession, LoginMenuRenderer loginMenuRenderer) {
        this.username = LoginMenuRegex.FORGET_PASSWORD.getGroup(input, "username");
        this.email = LoginMenuRegex.FORGET_PASSWORD.getGroup(input, "email");
        this.appSession = appSession;
        this.loginMenuRenderer = loginMenuRenderer;
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
        loginMenuRenderer.showSecurityQuestion(user);
        String input = InputHandler.readLine();
        if (input == null) {   // EOF: abort the reset
            return false;
        }
        input = input.trim();

        if (!LoginMenuRegex.ANSWER_SECURITY.matches(input)) {
            loginMenuRenderer.forgetPasswordRender(new Result(false, "That answer isn't in the right format."));
            return false;
        }

        String answer = LoginMenuRegex.ANSWER_SECURITY.getGroup(input, "answer");
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

    private void processPasswordReset(User user) {
        loginMenuRenderer.forgetPasswordRender(new Result(true, "Enter new password:"));
        String newPassword = InputHandler.readLine();
        if (newPassword == null) {   // EOF: abort the reset
            return;
        }
        newPassword = newPassword.trim();

        Result validationResult = new PasswordValidator().validate(newPassword);
        if (!validationResult.success()) {
            loginMenuRenderer.forgetPasswordRender(validationResult);
            return;
        }

        user.changePassword(PasswordHasher.hash(newPassword));
        DatabaseManager.getInstance().saveAll();

        loginMenuRenderer.forgetPasswordRender(new Result(true, "Password changed successfully!"));
    }
}
