package views.gdx.renderers;

import controllers.systems.NewsSystem;
import models.user.Profile;
import views.gdx.core.ToastSink;
import views.renderers.MenuRenderer.NewsMenuRenderer;

// The news menu.
//
// A newspaper does not fit in a toast, and NewsScreen (T5.1) is what will show it. What a toast CAN
// usefully carry is the one thing the player wants to know before opening it: how much is unread.
public final class GdxNewsMenuRenderer implements NewsMenuRenderer {

    private final ToastSink toasts;

    public GdxNewsMenuRenderer(ToastSink toasts) {
        this.toasts = toasts;
    }

    @Override
    public void unreadNewsRender(Profile profile) {
        int unread = NewsSystem.getInstance().getUnreadNews(profile).size();
        if (unread == 0) {
            toasts.info("All caught up -- not a single unread story.");
            return;
        }
        toasts.info(unread + (unread == 1 ? " story" : " stories") + " hot off the press.");
    }

    @Override
    public void allNewsRender(Profile profile) {
        int total = profile == null || profile.getNewsList() == null ? 0 : profile.getNewsList().size();
        if (total == 0) {
            toasts.info("No news yet. Go make some headlines!");
            return;
        }
        toasts.info(total + (total == 1 ? " story" : " stories") + " in the archive.");
    }

    @Override
    public void noUserLoggedIn() {
        toasts.error("Nobody's signed in -- log in first and the paper's all yours.");
    }

    @Override
    public void hasNoProfile() {
        toasts.error("This account has no profile attached. Curious.");
    }
}
