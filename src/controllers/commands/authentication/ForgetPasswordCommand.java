package controllers.commands.authentication;

import controllers.commands.Command;
import models.user.AppSession;
import models.user.User;
import utils.Result;
import utils.regex.LoginMenuRegex;
import utils.storage.DatabaseManager;
import utils.storage.PasswordHasher;
import utils.storage.RecoveryStart;
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
        // The account, the email check and the security question all come back in one step, and
        // deliberately WITHOUT the account itself.
        //
        // This used to fetch the whole User to read one field off it and then compare the security
        // answer locally. That is a credential leak once the roster is on a server: recovering a
        // password would require the server to hand the caller the very hashes they are trying to get
        // past, for any username they cared to type. Every comparison below now happens where the
        // secret lives; the local backend does exactly what this command used to, so the terminal
        // build behaves identically.
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        RecoveryStart start = databaseManager.beginRecovery(username, email);
        if (!start.ok()) {
            loginMenuRenderer.forgetPasswordRender(new Result(false, start.message()));
            return;
        }

        String answer = obtainSecurityAnswer(start);
        if (answer == null) {   // EOF, malformed, or cancelled -- already reported where it happened
            return;
        }
        if (SecurityAnswer.isBlank(answer)) {
            loginMenuRenderer.forgetPasswordRender(new Result(false, "An empty answer won't fool anyone."));
            return;
        }

        // Verified BEFORE the new password is asked for, so a wrong answer is reported straight away
        // rather than after the player has typed a password that was never going to be accepted.
        String answerHash = SecurityAnswer.hash(answer);
        Result verified = databaseManager.verifyRecoveryAnswer(username, answerHash);
        if (!verified.success()) {
            loginMenuRenderer.forgetPasswordRender(verified);
            return;
        }

        processPasswordReset(answerHash);
    }

    // The answer itself, however this front end gets one. Returns null when there is no answer to
    // check, having already said why.
    private String obtainSecurityAnswer(RecoveryStart start) {
        if (!interactive) {
            return suppliedSecurityAnswer;   // the screen showed the question and collected the answer
        }

        loginMenuRenderer.showSecurityQuestion(start.question());
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

    private void processPasswordReset(String answerHash) {
        String newPassword = obtainNewPassword();
        if (newPassword == null) {   // EOF: abort the reset
            return;
        }

        // Strength is checked HERE, on the plaintext, and it is the only place it can be: the backend
        // is handed a hash, and a hash cannot be graded. A remote backend therefore trusts its client
        // about strength -- see AccountService for why that trade is the right way round.
        Result validationResult = new PasswordValidator().validate(newPassword);
        if (!validationResult.success()) {
            loginMenuRenderer.forgetPasswordRender(validationResult);
            return;
        }

        // The answer travels again with the reset. The verify above is for the CONVERSATION -- so a
        // wrong answer is reported before a password is asked for -- and this is the check that
        // actually guards the change, which is why the backend re-checks rather than trusting that the
        // caller already did.
        Result reset = DatabaseManager.getInstance()
                .completeRecovery(username, answerHash, PasswordHasher.hash(newPassword));
        loginMenuRenderer.forgetPasswordRender(reset);
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
