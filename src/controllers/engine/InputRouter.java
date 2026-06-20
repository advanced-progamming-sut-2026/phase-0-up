package controllers.engine;


import utils.regex.AllMenuRegex;
import views.InputHandler;
import views.renderers.*;
import views.renderers.MenuRenderer.*;

import java.util.Scanner;

public class InputRouter {
    private MenuType currentMenu;
    private boolean running;
    private AllMenuRenderer allMenuRenderer = new AllMenuRenderer();
    private CollectionMenuRenderer collectionMenuRenderer = new CollectionMenuRenderer();
    private GameMenuRenderer gameMenuRenderer = new GameMenuRenderer();
    private LoginMenuRenderer loginMenuRenderer =  new LoginMenuRenderer();
    private MainMenuRenderer mainMenuRenderer = new MainMenuRenderer();
    private NewsMenuRenderer newsMenuRenderer = new NewsMenuRenderer();
    private PlantMenuRenderer plantMenuRenderer = new PlantMenuRenderer();
    private ProfileMenuRenderer profileMenuRenderer = new ProfileMenuRenderer();
    private SettingMenuRenderer settingMenuRenderer = new SettingMenuRenderer();
    private SignUpMenuRenderer signUpMenuRenderer = new SignUpMenuRenderer();
    private GreenhouseRenderer greenhouseRenderer = new GreenhouseRenderer();
    private LeaderboardRenderer leaderboardRenderer = new LeaderboardRenderer();
    private MapRenderer mapRenderer = new MapRenderer();
    private ShopRenderer shopRenderer = new ShopRenderer();
    private TravelLogRenderer travelLogRenderer = new TravelLogRenderer();

    public InputRouter() {
        this.currentMenu = MenuType.LOGIN_MENU;
        this.running = true;
    }

    public void startLoop(){
        while(running){
            String input = InputHandler.readLine().trim();

            if (AllMenuRegex.EXIT_MENU.matches(input)) {
                switch (currentMenu) {
                    case SIGNUP_MENU -> exit();
                    case LOGIN_MENU -> setCurrentMenu(MenuType.SIGNUP_MENU);
                    case PLAY_MENU, SETTINGS_MENU, ONLINE_MENU, NEWS_MENU, PROFILE_MENU,
                         PLANTS_MENU, SHOP_MENU, GREENHOUSE_MENU ->
                            setCurrentMenu(MenuType.MAIN_MENU);
                    case COLLECTION_MENU -> setCurrentMenu(MenuType.PLAY_MENU);
                }
            }


        }
    }
    public void routeAndExecute(String input){}
    public void setCurrentMenu(MenuType currentMenu) {
        this.currentMenu = currentMenu;
    }

    private void exit(){
        this.running = false;
    }
}
