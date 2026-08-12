package views.renderers.MenuRenderer;

// The main menu's button list and the sign-out acknowledgement.
public interface MainMenuRenderer {
    void logOutRender(boolean success);

    // hasUnreadNews drives the News button's notification badge. The Command computes it; how a badge
    // is drawn is the implementation's business.
    void showMainMenu(boolean hasUnreadNews);
}
