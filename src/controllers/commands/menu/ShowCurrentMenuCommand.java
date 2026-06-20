package controllers.commands.menu;

import controllers.commands.Command;
import models.user.AppSession;
import views.renderers.MenuRenderer.AllMenuRenderer;

public class ShowCurrentMenuCommand implements Command {
    AppSession appSession;
    AllMenuRenderer allMenuRenderer;

    public ShowCurrentMenuCommand(AppSession appSession, AllMenuRenderer allMenuRenderer) {
        this.appSession = appSession;
        this.allMenuRenderer = allMenuRenderer;
    }

    @Override
    public void execute() {
        allMenuRenderer.showCurrentMenu(appSession.getCurrentMenu());
    }
}
