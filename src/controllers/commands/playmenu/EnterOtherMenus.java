package controllers.commands.playmenu;

import controllers.commands.Command;
import controllers.engine.MenuType;
import models.user.AppSession;
import views.renderers.MenuRenderer.PlayMenuRenderer;

public class EnterOtherMenus implements Command {
    private MenuType newMenu;
    private AppSession appSession;
    public EnterOtherMenus(MenuType newMenu , AppSession appSession) {
        this.newMenu = newMenu;
        this.appSession = appSession;
    }

    @Override
    public void execute() {
        PlayMenuRenderer renderer = new PlayMenuRenderer();
        switch (newMenu){
            case LEADERBOARD -> {
                appSession.setCurrentMenu(MenuType.LEADERBOARD);
                break;
            }
            case GREENHOUSE_MENU -> {
                appSession.setCurrentMenu(MenuType.GREENHOUSE_MENU);
                break;
            }
            case TRAVEL_LOG_MENU -> {
                appSession.setCurrentMenu(MenuType.TRAVEL_LOG_MENU);
                break;
            }
        }
        renderer.enterOtherMenusFromThisMenu(newMenu.getMenuName());
    }
}
