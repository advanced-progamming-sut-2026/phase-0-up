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
            allMenuRenderer.enterMenu(new Result(false, "Invalid menu name!"));
            return;
        }

        MenuType currentMenu = appSession.getCurrentMenu();
        if (currentMenu == type){
            allMenuRenderer.enterMenu(new Result(false,
                    String.format("You are already on the %s menu!", type.getMenuName())));
            return;
        }

        switch (currentMenu){
            case SIGNUP_MENU :
                if (type == MenuType.LOGIN_MENU){
                    appSession.setCurrentMenu(MenuType.LOGIN_MENU);
                    allMenuRenderer.enterMenu(new Result(true, "Entered login menu!"));
                    return;
                }
            case LOGIN_MENU:
                if(appSession.getCurrentUser() != null){
                    if (type == MenuType.MAIN_MENU){
                        appSession.setCurrentMenu(type);
                        allMenuRenderer.enterMenu(new Result(true , String.format("Entered %s menu!", type.getMenuName())));
                        return;
                    }
                }
            case MAIN_MENU :
                if (type == MenuType.PLAY_MENU ||
                    type == MenuType.SETTINGS_MENU ||
                    type == MenuType.ONLINE_MENU ||
                    type == MenuType.NEWS_MENU ||
                    type == MenuType.PROFILE_MENU){
                    appSession.setCurrentMenu(type);
                    allMenuRenderer.enterMenu(new Result(true, String.format("Entered %s menu!", type.getMenuName())));
                    return;
                }
            case PLAY_MENU:
                if (type == MenuType.COLLECTION_MENU){
                    appSession.setCurrentMenu(MenuType.COLLECTION_MENU);
                    allMenuRenderer.enterMenu(new Result(true, "Entered collection menu!"));
                    return;
                }
            default:
                allMenuRenderer.enterMenu(new Result(false,
                        String.format("You can't enter %s menu from %s menu!",
                                type.getMenuName(), currentMenu.getMenuName())));
        }
    }
}
