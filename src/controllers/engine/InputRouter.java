package controllers.engine;


import controllers.commands.authentication.RegisterCommand;
import controllers.commands.menu.EnterMenuCommand;
import controllers.commands.authentication.LogoutCommand;
import controllers.commands.menu.ExitMenuCommand;
import controllers.commands.playmenu.EnterChapterCommand;
import controllers.commands.profileandsettings.ChangeDifficultyCommand;
import controllers.commands.menu.ShowCurrentMenuCommand;
import controllers.commands.profileandsettings.EditAction;
import controllers.commands.profileandsettings.ProfileCommands;
import controllers.commands.profileandsettings.ShowProfileCommand;
import controllers.commands.shopandeconomy.ShowShopCommand;
import models.shop.Currency;
import models.shop.Shop;
import models.user.AppSession;
import models.user.User;
import utils.regex.AllMenuRegex;
import utils.regex.MainMenuRegex;
import utils.regex.SettingMenuRegex;
import utils.regex.*;
import views.InputHandler;
import views.renderers.*;
import views.renderers.MenuRenderer.*;

public class InputRouter {
    private final AppSession appSession;

    private boolean running;

    private final AllMenuRenderer allMenuRenderer = new AllMenuRenderer();
    private final CollectionMenuRenderer collectionMenuRenderer = new CollectionMenuRenderer();
    private final PlayMenuRenderer gameMenuRenderer = new PlayMenuRenderer();
    private final LoginMenuRenderer loginMenuRenderer = new LoginMenuRenderer();
    private final MainMenuRenderer mainMenuRenderer = new MainMenuRenderer();
    private final NewsMenuRenderer newsMenuRenderer = new NewsMenuRenderer();
    private final PlantMenuRenderer plantMenuRenderer = new PlantMenuRenderer();
    private final ProfileMenuRenderer profileMenuRenderer = new ProfileMenuRenderer();
    private final SettingMenuRenderer settingMenuRenderer = new SettingMenuRenderer();
    private final SignUpMenuRenderer signUpMenuRenderer = new SignUpMenuRenderer();
    private final GreenhouseRenderer greenhouseRenderer = new GreenhouseRenderer();
    private final LeaderboardRenderer leaderboardRenderer = new LeaderboardRenderer();
    private final MapRenderer mapRenderer = new MapRenderer();
    private final ShopRenderer shopRenderer = new ShopRenderer();
    private final TravelLogRenderer travelLogRenderer = new TravelLogRenderer();

    public InputRouter(AppSession appSession) {
        this.running = true;
        this.appSession = appSession;
    }

    public void startLoop() {
        while (running) {
            String input = InputHandler.readLine().trim();

            routeAndExecute(input);

        }
    }

    private void routeAndExecute(String input) {
        if (AllMenuRegex.EXIT_MENU.matches(input)) {exitMenu(); return;}
        else if (AllMenuRegex.ENTER_MENU.matches(input)) {enterMenu(input); return;}
        else if (AllMenuRegex.SHOW_CURRENT.matches(input)) {new ShowCurrentMenuCommand(appSession, allMenuRenderer).execute(); return;}
        switch (appSession.getCurrentMenu()){
            case MAIN_MENU : {
                if(MainMenuRegex.LOG_OUT.matches(input)) {logout(); return;} break;}
            case SETTINGS_MENU : {
                if(SettingMenuRegex.CHANGE_DL.matches(input)){
                    changeDL(SettingMenuRegex.CHANGE_DL.getGroup(input , "dl")); return;} break;}
            case PROFILE_MENU : { if(handleProfileMenuExecute(input)) {return;} break;}
            case PLAY_MENU : { if(handlePlayMenuExecute(input)) {return;} break;}
            case SHOP_MENU : {if(handleShopMenuExecute(input)) {return;} break;}
            case SIGNUP_MENU :
                if (SignUpMenuRegex.SIGN_UP.matches(input)) new RegisterCommand(input, signUpMenuRenderer).execute();
                return;
            case LOGIN_MENU:
                if (LoginMenuRegex.LOGIN.matches(input)) new LoginCommand(input, appSession, loginMenuRenderer).execute();
                return;
        }

        allMenuRenderer.invalidCommand();
    }

    private boolean handleShopMenuExecute(String input) {
        Shop shop = appSession.getShop();
        if(ShopMenuRegex.SHOP_LIST.matches(input)){
            new ShowShopCommand("list" , shop).execute();
            return true;
        }
        else if(ShopMenuRegex.SHOP_DAILY.matches(input)){
            new ShowShopCommand("daily" , shop).execute();
            return true;
        }
        else if(ShopMenuRegex.BUY.matches(input)){
            new BuyShopItemCommand(Integer.parseInt(ShopMenuRegex.BUY.getGroup(input , "id")) , appSession.getShop() ,
                    Integer.parseInt(ShopMenuRegex.BUY.getGroup(input , "number")) ,
                    ShopMenuRegex.BUY.getGroup(input , "plantType") , appSession.getCurrentUser().getProfile()).execute();
            return true;
        }
        return false;
    }

    private boolean handlePlayMenuExecute(String input) {
        if(PlayMenuRegex.ENTER_CHAPTER.matches(input)){
            new EnterChapterCommand(PlayMenuRegex.ENTER_CHAPTER.getGroup(input , "chapter") ,
                    appSession.getCurrentUser().getProfile()).execute();
            return true;
        }
        else if(PlayMenuRegex.ENTER_GREENHOUSE.matches(input)){
            new EnterOtherMenus(MenuType.GREENHOUSE_MENU , appSession).execute();
            return true;
        }
        else if(PlayMenuRegex.ENTER_TRAVEL_LOG.matches(input)){
            new EnterOtherMenus(MenuType.TRAVEL_LOG_MENU , appSession).execute();
            return true;
        }
        else if(PlayMenuRegex.ENTER_LEADERBOARD.matches(input)){
            new EnterOtherMenus(MenuType.LEADERBOARD , appSession).execute();
            return true;
        }
        else if(PlayMenuRegex.SHOW_COINS.matches(input)){
            new ShowWalletCommand(appSession.getCurrentUser().getProfile() , Currency.COIN).execute();
            return true;
        }
        else if(PlayMenuRegex.SHOW_GEMS.matches(input)){
            new ShowWalletCommand(appSession.getCurrentUser().getProfile() , Currency.GEM).execute();
            return true;
        }
        else if(PlayMenuRegex.CHEAT_CODE.matches(input)){
            new CheatAddCommand(PlayMenuRegex.CHEAT_CODE.getGroup(input , "currency") ,
                    Integer.parseInt(PlayMenuRegex.CHEAT_CODE.getGroup(input , "n")) ,
                    appSession.getCurrentUser().getProfile()).execute();
            return true;
        }
        return false;
    }

    private boolean handleProfileMenuExecute(String input) {
        User user = appSession.getCurrentUser();
        if(ProfileMenuRegex.CHANGE_USERNAME.matches(input)) {
            new ProfileCommands( user, EditAction.USERNAME ,
                    ProfileMenuRegex.CHANGE_USERNAME.getGroup(input , "username") , null, profileMenuRenderer).execute();
            return true;
        }
        else if(ProfileMenuRegex.CHANGE_NICKNAME.matches(input)) {
            new ProfileCommands(user, EditAction.NICKNAME ,
                    ProfileMenuRegex.CHANGE_NICKNAME.getGroup(input , "nickname") , null, profileMenuRenderer).execute();
            return true;
        }
        else if(ProfileMenuRegex.CHANGE_EMAIL.matches(input)) {
            new ProfileCommands(user, EditAction.EMAIL ,
                    ProfileMenuRegex.CHANGE_EMAIL.getGroup(input , "email") , null, profileMenuRenderer).execute();
            return true;
        }
        else if(ProfileMenuRegex.CHANGE_EMAIL.matches(input)) {
            new ProfileCommands(user, EditAction.PASSWORD ,
                    ProfileMenuRegex.CHANGE_PASS.getGroup(input , "newP") ,
                    ProfileMenuRegex.CHANGE_PASS.getGroup(input , "oldP"), profileMenuRenderer).execute();
            return true;
        }
        else if(ProfileMenuRegex.SHOW_INFO.matches(input)) {
            new ShowProfileCommand(user).execute();
            return true;
        }
        return false;
    }

    private void changeDL(String dl) {
        ChangeDifficultyCommand command = new ChangeDifficultyCommand(appSession.getCurrentUser()
                , Integer.parseInt(dl));
        command.execute();
    }

    private void logout() {
        LogoutCommand command = new LogoutCommand(appSession);
        command.execute();
    }

    private void exitGame() {
        this.running = false;
    }


    private void exitMenu() {
        if (appSession.getCurrentMenu() == MenuType.SIGNUP_MENU) exitGame();
        ExitMenuCommand command = new ExitMenuCommand(appSession, allMenuRenderer);
        command.execute();
    }

    private void enterMenu(String input){
       String menuName = AllMenuRegex.ENTER_MENU.getGroup(input, "menuName");
       EnterMenuCommand command = new EnterMenuCommand(appSession, menuName, allMenuRenderer);
       command.execute();
    }

}

