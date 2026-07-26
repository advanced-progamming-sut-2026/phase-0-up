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

    private record SecurityQuestionData(int questionNumber, String answer) {}

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

        SecurityQuestionData securityData = handleSecurityQuestionInput();
        if (securityData == null) {   // EOF while picking a security question -> abort registration
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
        if (gender.equalsIgnoreCase("male")) return Gender.MALE;
        if (gender.equalsIgnoreCase("female")) return Gender.FEMALE;
        return null;
    }

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
