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
import utils.storage.AuthResult;
import utils.storage.PasswordHasher;
import views.renderers.MenuRenderer.AllMenuRenderer;
import views.renderers.MenuRenderer.LoginMenuRenderer;


public class LoginCommand implements Command {
    private String username;
    private String password;
    private boolean stayLoggedIn;
    private AppSession appSession;
    private LoginMenuRenderer loginMenuRenderer;
    private AllMenuRenderer allMenuRenderer;

    public LoginCommand(String input, AppSession appSession, LoginMenuRenderer loginMenuRenderer,
                        AllMenuRenderer allMenuRenderer) {
        username = LoginMenuRegex.LOGIN.getGroup(input, "username");
        password = LoginMenuRegex.LOGIN.getGroup(input, "password");
        stayLoggedIn = (LoginMenuRegex.LOGIN.getGroup(input, "stayLoggedIn") != null);
        this.appSession = appSession;
        this.loginMenuRenderer = loginMenuRenderer;
        this.allMenuRenderer = allMenuRenderer;
    }

    @Override
    public void execute() {
        // Verified by the storage backend, not here.
        //
        // This used to fetch the account and compare the hash locally, which is fine when the roster
        // is a file on this machine and a credential leak when it is not: the server would have to
        // hand a full account -- password hash included -- to a caller who has proved nothing, for any
        // username they cared to name. Hashing here and verifying there keeps both the plaintext and
        // the stored hash where they belong. The local backend does exactly what this method did, so
        // the terminal build is unaffected.
        AuthResult auth = DatabaseManager.getInstance()
                .authenticate(username, PasswordHasher.hash(password), stayLoggedIn);
        if (!auth.success()) {
            loginMenuRenderer.successOfLoggingIn(new Result(false, auth.message()));
            return;
        }
        User user = auth.user();

        appSession.setCurrentUser(user);
        // A saved profile is deserialized past the constructor; re-grant its starter plants so seed
        // selection isn't stuck on "locked" for every plant.
        user.getProfile().ensureStartingPlants();
        // Chapters/levels aren't persisted; rebuild the campaign graph from progress at login.
        LevelInitializer.attachCampaign(user.getProfile());
        // The stay-signed-in flag -- and clearing everybody else's -- is applied by authenticate(),
        // because only the side holding the roster can make "exactly one account" true.

        DatabaseManager.getInstance().saveAll();
        EnterMenuCommand enterMenuCommand = new EnterMenuCommand(appSession,
                MenuType.MAIN_MENU.getMenuName(), allMenuRenderer);
        enterMenuCommand.execute();

        loginMenuRenderer.successOfLoggingIn(new Result(true, "Welcome back! The lawn missed you."));

    }

}
