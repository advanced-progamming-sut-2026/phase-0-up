import controllers.engine.InputRouter;
import controllers.engine.MenuType;
import models.user.AppSession;
import models.user.User;
import utils.gameinitializers.GameInitializer;
import utils.storage.DatabaseManager;

public class Main {

    public static void main(String[] args) {
        GameInitializer gameInitializer = new GameInitializer();
        gameInitializer.loadAllData();

        DatabaseManager db =  DatabaseManager.getInstance();

        User autoLoggedInUser = db.getLoggedInUser();
        AppSession appSession = new AppSession();

        if (autoLoggedInUser != null) {
            System.out.println("Auto-logging User: " + autoLoggedInUser.getUsername());
            appSession.setCurrentUser(autoLoggedInUser);
            appSession.setCurrentMenu(MenuType.MAIN_MENU);
        }

        InputRouter router = new InputRouter(appSession);
        router.startLoop();
    }
}
