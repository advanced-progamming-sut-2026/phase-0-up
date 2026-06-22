package views.renderers.MenuRenderer;

import controllers.systems.NewsSystem;
import models.news.News;
import models.user.Profile;
import views.OutputHandler;

import java.util.List;

public class NewsMenuRenderer {
    public void unreadNewsRender(Profile profile){
        List<News> unread = NewsSystem.getInstance().getUnreadNews(profile);
        if (unread.isEmpty()) {
            OutputHandler.showMessage("You have no unread news.");
            return;
        }
        OutputHandler.showMessage("Unread news:");
        for (News news : unread) {
            OutputHandler.showMessage(format(news));
        }
    }
    public void allNewsRender(Profile profile){
        if (profile == null || profile.getNewsList() == null || profile.getNewsList().isEmpty()) {
            OutputHandler.showMessage("You have no news yet.");
            return;
        }
        OutputHandler.showMessage("All news:");
        for (News news : profile.getNewsList()) {
            OutputHandler.showMessage(format(news));
        }
    }

    private String format(News news) {
        String status = news.isRead() ? "[read]" : "[unread]";
        return status + " #" + news.getId()
                + " | " + news.getTitle()
                + " - " + news.getDescription()
                + " (" + news.getAuthor() + ", " + news.getDate() + ")";
    }

    public void noUserLoggedIn(){
        OutputHandler.showError("No user is currently logged in.");
    }

    public void hasNoProfile(){
        OutputHandler.showError("Current user has no profile.");
    }
}
