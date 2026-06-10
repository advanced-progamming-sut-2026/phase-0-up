package controllers.engine;


import java.util.Scanner;

public class InputRouter {
    private MenuType currentMenu;

    public InputRouter() {
        this.currentMenu = MenuType.LOGIN_MENU;
    }

    public void startLoop(){}
    public void routeAndExecute(String input){}
    public void setCurrentMenu(MenuType currentMenu) {
        this.currentMenu = currentMenu;
    }
}
