package controllers.commands.menu;

import controllers.commands.Command;
import controllers.engine.MenuType;
import controllers.systems.NewsSystem;
import models.user.AppSession;
import models.user.User;
import views.renderers.MenuRenderer.AllMenuRenderer;
import views.renderers.MenuRenderer.MainMenuRenderer;

public class ShowCurrentMenuCommand implements Command {
    AppSession appSession;
    AllMenuRenderer allMenuRenderer;
    MainMenuRenderer mainMenuRenderer;

    public ShowCurrentMenuCommand(AppSession appSession, AllMenuRenderer allMenuRenderer,
                                  MainMenuRenderer mainMenuRenderer) {
        this.appSession = appSession;
        this.allMenuRenderer = allMenuRenderer;
        this.mainMenuRenderer = mainMenuRenderer;
    }

    @Override
    public void execute() {
        // On the main menu, draw the button list with the News button's unread-news badge; every other
        // menu keeps the simple "Current Menu: <name>" line.
        if (appSession.getCurrentMenu() == MenuType.MAIN_MENU) {
            User user = appSession.getCurrentUser();
            boolean hasUnread = user != null
                    && NewsSystem.getInstance().hasUnreadNews(user.getProfile());
            mainMenuRenderer.showMainMenu(hasUnread);
            return;
        }
        allMenuRenderer.showCurrentMenu(appSession.getCurrentMenu());
    }
}
