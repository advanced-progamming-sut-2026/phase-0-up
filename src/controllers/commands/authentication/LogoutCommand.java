package controllers.commands.authentication;

import controllers.commands.Command;
import controllers.engine.MenuType;
import models.user.AppSession;
import views.renderers.MenuRenderer.MainMenuRenderer;

public class LogoutCommand implements Command {
    private AppSession appSession;

    public LogoutCommand(AppSession appSession) {
        this.appSession = appSession;
    }

    @Override
    public void execute() {
        MainMenuRenderer mainMenuRenderer = new MainMenuRenderer();
        if(!appSession.isLoggedIn()){
            mainMenuRenderer.logOutRender(false);
            return;
        }
        appSession.setCurrentUser(null);
        appSession.setCurrentMenu(MenuType.SIGNUP_MENU);
        mainMenuRenderer.logOutRender(true);
    }
}
