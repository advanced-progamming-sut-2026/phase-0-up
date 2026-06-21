package controllers.commands.authentication;

import controllers.commands.Command;
import controllers.engine.MenuType;
import models.user.AppSession;
import views.renderers.MenuRenderer.MainMenuRenderer;

public class LogoutCommand implements Command {
    private AppSession appSession;
    private MainMenuRenderer mainMenuRenderer;

    public LogoutCommand(AppSession appSession, MainMenuRenderer mainMenuRenderer) {
        this.appSession = appSession;
        this.mainMenuRenderer = mainMenuRenderer;
    }

    @Override
    public void execute() {
        if(!appSession.isLoggedIn()){
            mainMenuRenderer.logOutRender(false);
            return;
        }
        appSession.setCurrentUser(null);
        appSession.setCurrentMenu(MenuType.SIGNUP_MENU);
        mainMenuRenderer.logOutRender(true);
    }
}
