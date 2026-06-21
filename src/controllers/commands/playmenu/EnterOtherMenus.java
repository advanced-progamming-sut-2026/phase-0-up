package controllers.commands.playmenu;

import controllers.commands.Command;
import controllers.engine.MenuType;
import models.user.AppSession;
import views.renderers.MenuRenderer.PlayMenuRenderer;

public class EnterOtherMenus implements Command {
    private MenuType newMenu;
    private AppSession appSession;
    private PlayMenuRenderer renderer;

    public EnterOtherMenus(MenuType newMenu , AppSession appSession, PlayMenuRenderer renderer) {
        this.newMenu = newMenu;
        this.appSession = appSession;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
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
