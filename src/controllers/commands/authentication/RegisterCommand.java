package controllers.commands.authentication;

import controllers.commands.Command;
import controllers.commands.menu.EnterMenuCommand;
import controllers.engine.MenuType;
import models.user.AppSession;
import models.user.Gender;
import models.user.User;
import utils.Constants;
import utils.Result;
import utils.regex.SignUpMenuRegex;
import utils.storage.DatabaseManager;
import utils.storage.PasswordHasher;
import utils.storage.SecurityAnswer;
import utils.validation.*;
import views.InputHandler;
import views.OutputHandler;
import views.renderers.MenuRenderer.AllMenuRenderer;
import views.renderers.MenuRenderer.SignUpMenuRenderer;

// Registration, in two flavours.
//
// The terminal flavour is a conversation: it prints the security questions and loops on stdin until
// the player picks one properly. That loop is a blocking read, and running it on LibGDX's render
// thread would freeze the window -- so there is a second constructor that takes the completed form.
//
// Both flavours run the same validators and produce the same sentences. What differs is only where the
// security question and answer come from.
public class RegisterCommand implements Command {
    private final String username;
    private final String password;
    private final String passwordConfirm;
    private final String nickname;
    private final String email;
    private final String gender;
    private final SignUpMenuRenderer signUpMenuRenderer;
    private final AppSession appSession;
    private final AllMenuRenderer allMenuRenderer;

    // Set only in the non-interactive flavour; null means "ask the player".
    private final SecurityQuestionData suppliedSecurity;
    private final boolean interactive;

    private record SecurityQuestionData(int questionNumber, String answer) {}

    // The whole sign-up form, as a front end that collects it all before submitting would have it.
    // questionNumber is 1-based, matching what the terminal asks the player to type.
    public record Form(String username, String password, String passwordConfirm, String nickname,
                       String email, String gender, int questionNumber, String securityAnswer) {}

    public RegisterCommand(String input, SignUpMenuRenderer signUpMenuRenderer,
                           AppSession appSession, AllMenuRenderer allMenuRenderer) {
        this.username = SignUpMenuRegex.SIGN_UP.getGroup(input, "username");
        this.password = SignUpMenuRegex.SIGN_UP.getGroup(input, "password");
        this.passwordConfirm = SignUpMenuRegex.SIGN_UP.getGroup(input, "passwordConfirm");
        this.nickname = SignUpMenuRegex.SIGN_UP.getGroup(input, "nickname");
        this.email = SignUpMenuRegex.SIGN_UP.getGroup(input, "email");
        this.gender = SignUpMenuRegex.SIGN_UP.getGroup(input, "gender");
        this.signUpMenuRenderer = signUpMenuRenderer;
        this.appSession = appSession;
        this.allMenuRenderer = allMenuRenderer;
        this.suppliedSecurity = null;
        this.interactive = true;
    }

    // Non-interactive: nothing on this path reads stdin, so it is safe to call from a render thread.
    public RegisterCommand(Form form, SignUpMenuRenderer signUpMenuRenderer,
                           AppSession appSession, AllMenuRenderer allMenuRenderer) {
        this.username = form.username();
        this.password = form.password();
        this.passwordConfirm = form.passwordConfirm();
        this.nickname = form.nickname();
        this.email = form.email();
        this.gender = form.gender();
        this.signUpMenuRenderer = signUpMenuRenderer;
        this.appSession = appSession;
        this.allMenuRenderer = allMenuRenderer;
        this.suppliedSecurity = new SecurityQuestionData(form.questionNumber(),
                form.securityAnswer() == null ? "" : form.securityAnswer());
        this.interactive = false;
    }

    @Override
    public void execute() {
        if (!validateCredentials()) {
            return;
        }

        Gender genderType = parseGender();
        if (genderType == null) {
            signUpMenuRenderer.register(new Result(false, "Invalid gender"));
            return;
        }

        SecurityQuestionData securityData = interactive
                ? handleSecurityQuestionInput()
                : validateSuppliedSecurity();
        if (securityData == null) {   // EOF, or a form that did not pass -- already reported
            return;
        }

        registerNewUser(genderType, securityData.questionNumber(), securityData.answer());
    }

    private boolean validateCredentials() {
        Result userNameResult = new UsernameValidator().validate(username);
        if (!userNameResult.success()) {
            signUpMenuRenderer.register(userNameResult);
            return false;
        }
        Result passwordResult = new PasswordValidator().validate(password);
        if (!passwordResult.success()) {
            signUpMenuRenderer.register(passwordResult);
            return false;
        }
        if (!passwordConfirm.equals(password)) {
            signUpMenuRenderer.register(new Result(false, "Passwords do not match"));
            return false;
        }
        if (!new NicknameValidator().validate(nickname).success()) {
            signUpMenuRenderer.register(new NicknameValidator().validate(nickname));
            return false;
        }
        if (!new EmailValidator().validate(email).success()) {
            signUpMenuRenderer.register(new EmailValidator().validate(email));
            return false;
        }
        return true;
    }

    private Gender parseGender() {
        if (gender == null) return null;
        if (gender.equalsIgnoreCase("male")) return Gender.MALE;
        if (gender.equalsIgnoreCase("female")) return Gender.FEMALE;
        return null;
    }

    // The form's own security answers, checked once. There is no retry loop here on purpose: a form
    // front end re-submits, it does not converse, so a bad field is reported and the command ends.
    private SecurityQuestionData validateSuppliedSecurity() {
        int questionNumber = suppliedSecurity.questionNumber();
        if (questionNumber < 1 || questionNumber > Constants.SECURITY_QUESTIONS.length) {
            signUpMenuRenderer.register(new Result(false, "Pick a question by number, 1 to "
                    + Constants.SECURITY_QUESTIONS.length + "."));
            return null;
        }
        if (SecurityAnswer.isBlank(suppliedSecurity.answer())) {
            signUpMenuRenderer.register(new Result(false, "An empty answer won't fool anyone. Try again!"));
            return null;
        }
        return suppliedSecurity;
    }

    // The terminal conversation. Its retry hints go straight to OutputHandler rather than through the
    // renderer because they are prompt affordances -- "type it like this" only means anything to
    // somebody who is typing. Nothing here runs on the graphical path.
    private SecurityQuestionData handleSecurityQuestionInput() {
        int questionNumber;
        String answer;

        while (true) {
            signUpMenuRenderer.showSecurityQuestions();
            String input = InputHandler.readLine();
            if (input == null) {   // EOF -> cancel registration
                return null;
            }

            if (!SignUpMenuRegex.SECURITY_QUESTION.matches(input)) {
                // Without this the loop silently re-printed the question list forever, leaving the
                // player with no idea their command was malformed.
                OutputHandler.showMessage("Use: pick question -q <1-5> -a <answer> -c <answer>");
                continue;
            }

            String numberString = SignUpMenuRegex.SECURITY_QUESTION.getGroup(input, "questionNumber");

            // Bounded to the digits a question number can plausibly have: "-q 99999999999" matches
            // \d+ but overflows parseInt, which used to crash registration outright.
            if (!numberString.matches("\\d{1,2}")) {
                OutputHandler.showMessage("Invalid question number");
                continue;
            }

            questionNumber = Integer.parseInt(numberString);
            if (questionNumber < 1 || questionNumber > Constants.SECURITY_QUESTIONS.length) {
                OutputHandler.showMessage("Pick a question by number, 1 to "
                        + Constants.SECURITY_QUESTIONS.length + ".");
                continue;
            }

            answer = SignUpMenuRegex.SECURITY_QUESTION.getGroup(input, "answer");
            String answerConfirm = SignUpMenuRegex.SECURITY_QUESTION.getGroup(input, "answerConfirm");

            if (SecurityAnswer.isBlank(answer)) {
                OutputHandler.showMessage("An empty answer won't fool anyone. Try again!");
                continue;
            }

            // Compared the same way recovery will compare it, so "Fluffy" and "fluffy " can never be
            // accepted as a matching pair here and then fail to match later.
            if (!SecurityAnswer.sameAnswer(answer, answerConfirm)) {
                OutputHandler.showMessage("Those two answers don't match. Try again!");
                continue;
            }
            break;
        }
        return new SecurityQuestionData(questionNumber, answer);
    }

    private void registerNewUser(Gender genderType, int questionNumber, String questionAnswer) {
        String hashedPassword = PasswordHasher.hash(password);
        // Hashed through SecurityAnswer, never PasswordHasher directly: recovery verifies against the
        // same canonical form, and hashing a raw answer here is what made every recovery attempt fail.
        String hashedQuestionAnswer = SecurityAnswer.hash(questionAnswer);
        boolean added = DatabaseManager.getInstance().addUser(new User(username, nickname, email, genderType,
                hashedPassword, questionNumber - 1, hashedQuestionAnswer));
        if (!added) {
            signUpMenuRenderer.register(new Result(false, "Username already exists"));
            return;
        }
        DatabaseManager.getInstance().saveAll();

        signUpMenuRenderer.register(new Result(true, "User successfully registered"));
        new EnterMenuCommand(appSession, MenuType.LOGIN_MENU.getMenuName(), allMenuRenderer).execute();
    }
}
