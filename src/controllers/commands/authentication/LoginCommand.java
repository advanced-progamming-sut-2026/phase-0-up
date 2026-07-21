package controllers.commands.authentication;


import controllers.commands.Command;
import controllers.commands.menu.EnterMenuCommand;
import controllers.engine.MenuType;
import models.user.AppSession;
import models.user.User;
import utils.Result;
import utils.regex.LoginMenuRegex;
import utils.gameinitializers.LevelInitializer;
import utils.storage.DatabaseManager;
import utils.storage.PasswordHasher;
import views.renderers.MenuRenderer.AllMenuRenderer;
import views.renderers.MenuRenderer.LoginMenuRenderer;

import javax.print.attribute.standard.MediaName;

public class LoginCommand implements Command {
    private String username;
    private String password;
    private boolean stayLoggedIn;
    private AppSession appSession;
    private LoginMenuRenderer loginMenuRenderer;
    private AllMenuRenderer allMenuRenderer;

    public LoginCommand(String input, AppSession appSession, LoginMenuRenderer loginMenuRenderer, AllMenuRenderer allMenuRenderer) {
        username = LoginMenuRegex.LOGIN.getGroup(input, "username");
        password = LoginMenuRegex.LOGIN.getGroup(input, "password");
        stayLoggedIn = (LoginMenuRegex.LOGIN.getGroup(input, "stayLoggedIn") != null);
        this.appSession = appSession;
        this.loginMenuRenderer = loginMenuRenderer;
        this.allMenuRenderer = allMenuRenderer;
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
        // A saved profile is deserialized past the constructor; re-grant its starter plants so seed
        // selection isn't stuck on "locked" for every plant.
        user.getProfile().ensureStartingPlants();
        // Chapters/levels aren't persisted; rebuild the campaign graph from progress at login.
        LevelInitializer.attachCampaign(user.getProfile());
        if (stayLoggedIn) {
            clearAllStayLoggedInFlags();
            user.setStayLoggedIn(true);
        } else {
            user.setStayLoggedIn(false);
        }

        DatabaseManager.getInstance().saveAll();
        EnterMenuCommand enterMenuCommand = new EnterMenuCommand(appSession , MenuType.MAIN_MENU.getMenuName(), allMenuRenderer);
        enterMenuCommand.execute();

        loginMenuRenderer.successOfLoggingIn(new Result(true, "Welcome back! The lawn missed you."));

    }

    private void clearAllStayLoggedInFlags() {
        User loggedInUser;
        while ((loggedInUser = DatabaseManager.getInstance().getLoggedInUser()) != null) {
            loggedInUser.setStayLoggedIn(false);
        }
    }
}
