package controllers.commands.news;

import controllers.commands.Command;
import controllers.systems.NewsSystem;
import models.user.Profile;
import models.user.User;
import views.renderers.MenuRenderer.NewsMenuRenderer;

public class ShowNewsCommand implements Command {
    private User currentUser;
    private NewsViewType type;
    private NewsMenuRenderer renderer;

    public ShowNewsCommand(User currentUser, NewsViewType type, NewsMenuRenderer renderer) {
        this.currentUser = currentUser;
        this.type = type;
        this.renderer = renderer;
    }

    @Override
    public void execute() {
        if(currentUser == null){
            renderer.noUserLoggedIn();
            return;
        }
        Profile profile = currentUser.getProfile();
        if(profile == null){
            renderer.hasNoProfile();
            return;
        }
        if (type == NewsViewType.UNREAD) {
            renderer.unreadNewsRender(profile);
            NewsSystem.getInstance().markAllRead(profile);
        } else {
            renderer.allNewsRender(profile);
        }
    }
}
