package controllers.commands.menu;

import controllers.commands.Command;
import controllers.engine.MenuType;
import models.user.AppSession;
import utils.Result;
import views.renderers.MenuRenderer.AllMenuRenderer;

public class EnterMenuCommand implements Command {
    private AppSession appSession;
    private String menuName;
    private AllMenuRenderer allMenuRenderer;

    public EnterMenuCommand(AppSession appSession, String menuName, AllMenuRenderer allMenuRenderer) {
        this.menuName = menuName;
        this.appSession = appSession;
        this.allMenuRenderer = allMenuRenderer;
    }

    @Override
    public void execute() {
        MenuType type = MenuType.fromName(menuName);

        if (type == null){
            allMenuRenderer.enterMenu(new Result(false, "There's no menu by that name around here."));
            return;
        }
        if (!isReachable(type)) {
            return;
        }

        MenuType currentMenu = appSession.getCurrentMenu();
        if (currentMenu == type){
            allMenuRenderer.enterMenu(new Result(false,
                    String.format("You are already on the %s menu!", type.getMenuName())));
            return;
        }

        if (isTransitionAllowed(currentMenu, type)) {
            enter(type);
        } else {
            allMenuRenderer.enterMenu(new Result(false,
                    String.format("You can't enter %s menu from %s menu!",
                            type.getMenuName(), currentMenu.getMenuName())));
        }
    }

    // Every menu except sign-up and login is behind authentication. Without this gate a logged-out
    // visitor could walk from SIGNUP_MENU straight into the profile/play/settings/news menus, where
    // commands then dereferenced a null current user. Reports the refusal itself.
    private boolean isReachable(MenuType type) {
        if (type != MenuType.SIGNUP_MENU && type != MenuType.LOGIN_MENU
                && appSession.getCurrentUser() == null) {
            allMenuRenderer.enterMenu(new Result(false, "Log in first -- the lawn is members only."));
            return false;
        }
        return true;
    }

    // The menu graph: which menu you may step into from where. Written as explicit edges rather than a
    // fall-through switch, which previously let one case leak into the next.
    private boolean isTransitionAllowed(MenuType from, MenuType to) {
        switch (from) {
            case SIGNUP_MENU:
                return to == MenuType.LOGIN_MENU;
            case LOGIN_MENU:
                return to == MenuType.MAIN_MENU && appSession.getCurrentUser() != null;
            case MAIN_MENU:
                return to == MenuType.PLAY_MENU
                        || to == MenuType.SETTINGS_MENU
                        || to == MenuType.ONLINE_MENU
                        || to == MenuType.NEWS_MENU
                        || to == MenuType.PROFILE_MENU;
            case PLAY_MENU:
                return to == MenuType.COLLECTION_MENU;
            default:
                return false;
        }
    }

    // The login and collection menus keep their own greetings, exactly as before the switch was
    // flattened -- these strings are what the player already sees.
    private void enter(MenuType type) {
        appSession.setCurrentMenu(type);
        String message;
        if (type == MenuType.LOGIN_MENU) {
            message = "Entered login menu!";
        } else if (type == MenuType.COLLECTION_MENU) {
            message = "Entered collection menu!";
        } else {
            message = String.format("Welcome to the %s menu!", type.getMenuName());
        }
        allMenuRenderer.enterMenu(new Result(true, message));
    }
}
