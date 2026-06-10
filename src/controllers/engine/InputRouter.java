package controllers.engine;


import java.util.Scanner;

public class InputRouter {
    private final Scanner scanner;
    private MenuType currentMenu;

    public InputRouter(Scanner scanner) {
        this.scanner = scanner;
        this.currentMenu = MenuType.LOGIN_MENU;
    }

    public void startLoop(){}
    public void routeAndExecute(String input){}
    public void setCurrentMenu(MenuType currentMenu) {
        this.currentMenu = currentMenu;
    }
}
