import controllers.engine.InputRouter;
import controllers.engine.MenuType;
import models.user.AppSession;
import models.user.User;
import utils.gameinitializers.GameInitializer;
import utils.gameinitializers.LevelInitializer;
import utils.storage.DatabaseManager;

public class Main {

    public static void main(String[] args) {
        GameInitializer gameInitializer = new GameInitializer();
        gameInitializer.loadAllData();

        // Wire the model's balance-change hook to the view. The model publishes, the view renders, and
        // this composition root is the only place that knows about both -- so Profile stays view-free.
        views.renderers.CurrencyRenderer currencyRenderer = new views.renderers.CurrencyRenderer();
        models.user.Profile.setCurrencyObserver(currencyRenderer::showBalance);

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

        InputRouter router = new InputRouter(appSession);
        router.startLoop();
    }
}
