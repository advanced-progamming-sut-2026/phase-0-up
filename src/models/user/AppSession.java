package models.user;

import controllers.engine.GameEngine;
import controllers.engine.MenuType;
import models.game.GameSession;
import models.shop.Shop;

public class AppSession {
    private User currentUser;
    private MenuType currentMenu;
    private GameSession currentGameSession;
    private Shop shop;

    public AppSession() {
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

    public GameSession getCurrentGameSession() {
        return currentGameSession;
    }

    public void setCurrentGameSession(GameSession currentGameSession) {
        this.currentGameSession = currentGameSession;
    }
}