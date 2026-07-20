package controllers.commands.menu;

import controllers.commands.Command;
import controllers.engine.MenuType;
import models.user.AppSession;
import views.renderers.MenuRenderer.AllMenuRenderer;

public class ExitMenuCommand implements Command {
    private AppSession appSession;
    private AllMenuRenderer allMenuRenderer;

    public ExitMenuCommand(AppSession appSession, AllMenuRenderer renderer) {
        this.appSession = appSession;
        this.allMenuRenderer = renderer;
    }

    @Override
    public void execute() {
        MenuType currentMenu = appSession.getCurrentMenu();
        switch (currentMenu) {
            case LOGIN_MENU:
                appSession.setCurrentMenu(MenuType.SIGNUP_MENU);
                allMenuRenderer.menuExit("Sign Up");
                break;
            case PLAY_MENU:
            case SETTINGS_MENU:
            case ONLINE_MENU:
            case NEWS_MENU:
            case PROFILE_MENU:
                appSession.setCurrentMenu(MenuType.MAIN_MENU);
                allMenuRenderer.menuExit("Main Menu");
                break;
            case SHOP_MENU:
                appSession.setCurrentMenu(MenuType.GREENHOUSE_MENU);
                allMenuRenderer.menuExit("Greenhouse Menu");
                break;
            case GREENHOUSE_MENU:
            case PLANTS_MENU:
            case COLLECTION_MENU:
            case LEADERBOARD:
            case TRAVEL_LOG_MENU:
                appSession.setCurrentMenu(MenuType.PLAY_MENU);
                allMenuRenderer.menuExit("Play Menu");
                break;
        }
    }
}
