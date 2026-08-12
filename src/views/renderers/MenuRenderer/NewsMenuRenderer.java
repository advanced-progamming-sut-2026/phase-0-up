package views.renderers.MenuRenderer;

import models.user.Profile;

// The news menu. Note it takes the Profile rather than a prepared list: read state is part of what gets
// rendered, and the unread set is derived from the profile at render time.
public interface NewsMenuRenderer {
    void unreadNewsRender(Profile profile);

    void allNewsRender(Profile profile);

    void noUserLoggedIn();

    void hasNoProfile();
}
