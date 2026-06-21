package models.user;

import controllers.engine.MenuType;
import models.shop.Shop;

public class AppSession {
    private User currentUser;
    private MenuType currentMenu;
    private Shop shop;

    public AppSession(User currentUser) {
        this.currentUser = currentUser;
        currentMenu = MenuType.SIGNUP_MENU;
        this.shop = new Shop();
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

    public Shop getShop(){return this.shop;}
}