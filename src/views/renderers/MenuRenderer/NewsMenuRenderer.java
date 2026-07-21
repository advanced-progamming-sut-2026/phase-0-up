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
            OutputHandler.showMessage("All caught up -- not a single unread story.");
            return;
        }
        OutputHandler.showMessage("--- Hot off the press ---");
        for (News news : unread) {
            OutputHandler.showMessage(format(news));
        }
    }
    public void allNewsRender(Profile profile){
        if (profile == null || profile.getNewsList() == null || profile.getNewsList().isEmpty()) {
            OutputHandler.showMessage("No news yet. Go make some headlines!");
            return;
        }
        OutputHandler.showMessage("--- The whole newspaper ---");
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
        OutputHandler.showError("Nobody's signed in -- log in first and the paper's all yours.");
    }

    public void hasNoProfile(){
        OutputHandler.showError("This account has no profile attached. Curious.");
    }
}
