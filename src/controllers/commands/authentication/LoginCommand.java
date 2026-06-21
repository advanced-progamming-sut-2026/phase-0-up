package controllers.commands.authentication;


import controllers.commands.Command;
import models.user.AppSession;
import models.user.User;
import utils.Result;
import utils.regex.LoginMenuRegex;
import utils.storage.DatabaseManager;
import utils.storage.PasswordHasher;
import views.renderers.MenuRenderer.LoginMenuRenderer;

public class LoginCommand implements Command {
    private String username;
    private String password;
    private boolean stayLoggedIn;
    private AppSession appSession;
    private LoginMenuRenderer loginMenuRenderer;

    public LoginCommand(String input, AppSession appSession, LoginMenuRenderer loginMenuRenderer) {
        username = LoginMenuRegex.LOGIN.getGroup(input, "username");
        password = LoginMenuRegex.LOGIN.getGroup(input, "password");
        stayLoggedIn = (LoginMenuRegex.LOGIN.getGroup(input, "stayLoggedIn") != null);
        this.appSession = appSession;
        this.loginMenuRenderer = loginMenuRenderer;
    }

    @Override
    public void execute() {
        User user = DatabaseManager.getInstance().findUser(username);
        if (user == null) {
            loginMenuRenderer.successOfLoggingIn(new Result(false, "User not found"));
            return;
        }

        if (!PasswordHasher.matches(password, user.getHashPassword())) {
            loginMenuRenderer.successOfLoggingIn(new Result(false, "Wrong password"));
            return;
        }

        appSession.setCurrentUser(user);
        if (stayLoggedIn) {
            clearAllStayLoggedInFlags();
            user.setStayLoggedIn(true);
        } else {
            user.setStayLoggedIn(false);
        }

        DatabaseManager.getInstance().saveAll();

        loginMenuRenderer.successOfLoggingIn(new Result(true, "Logged in successfully"));

    }

    private void clearAllStayLoggedInFlags() {
        User loggedInUser;
        while ((loggedInUser = DatabaseManager.getInstance().getLoggedInUser()) != null) {
            loggedInUser.setStayLoggedIn(false);
        }
    }
}
