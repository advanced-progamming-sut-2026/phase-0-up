import controllers.engine.InputRouter;
import controllers.engine.MenuType;
import models.user.AppSession;
import models.user.User;
import utils.gameinitializers.GameInitializer;
import utils.gameinitializers.LevelInitializer;
import utils.storage.DatabaseManager;
import utils.storage.LocalFileBackend;

public class Main {

    public static void main(String[] args) {
        GameInitializer gameInitializer = new GameInitializer();
        gameInitializer.loadAllData();

        // Accounts stay on this machine for the terminal build, deliberately and permanently.
        //
        // `gradlew run` is this project's regression harness -- the same models, systems and 57
        // Commands the graphical build uses, driven from a prompt with no window and no GL context.
        // Requiring a server to run it would mean a change could not be checked without starting one,
        // which is exactly the friction that makes a harness stop being used.
        //
        // Stated before the first getInstance(), because that builds a default backend if nobody has
        // said otherwise and it would then be too late.
        DatabaseManager.setBackend(new LocalFileBackend());

        // The composition root picks the View. Everything downstream -- InputRouter, GameEngine, all 57
        // Commands -- is written against the renderer interfaces and never learns which one it got, so
        // this line is the entire difference between the terminal build and the graphical one.
        views.Renderers renderers = new views.console.ConsoleRenderers();

        // Wire the model's balance-change hook to the view. The model publishes, the view renders, and
        // this composition root is the only place that knows about both -- so Profile stays view-free.
        models.user.Profile.setCurrencyObserver(renderers.currency()::showBalance);

        DatabaseManager db =  DatabaseManager.getInstance();

        User autoLoggedInUser = db.getLoggedInUser();
        AppSession appSession = new AppSession();

        if (autoLoggedInUser != null) {
            System.out.println("Auto-logging User: " + autoLoggedInUser.getUsername());
            appSession.setCurrentUser(autoLoggedInUser);
            // A saved profile is deserialized past the constructor, so re-grant the starter plants it
            // would otherwise be missing (every seed would read as "locked" without this).
            autoLoggedInUser.getProfile().ensureStartingPlants();
            // Chapters/levels are never persisted, so an auto-logged-in user needs the same campaign
            // rebuild LoginCommand does -- without it currentChapter stays null and picking a level NPEs.
            LevelInitializer.attachCampaign(autoLoggedInUser.getProfile());
            appSession.setCurrentMenu(MenuType.MAIN_MENU);
        }

        InputRouter router = new InputRouter(appSession, renderers);
        router.startLoop();
    }
}
