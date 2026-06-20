package models.user;

import controllers.engine.MenuType;

public class AppSession {
    private User currentUser;
    private MenuType currentMenu;

    public AppSession(User currentUser) {
        this.currentUser = currentUser;
        currentMenu = MenuType.SIGNUP_MENU;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public MenuType getCurrentMenu() {
        return currentMenu;
    }

    public void setCurrentMenu(MenuType currentMenu) {
        this.currentMenu = currentMenu;
    }
}