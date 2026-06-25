package controllers.engine;


import controllers.commands.authentication.ForgetPasswordCommand;
import controllers.commands.authentication.LoginCommand;
import controllers.commands.authentication.RegisterCommand;
import controllers.commands.collection.*;
import controllers.commands.greenhouse.CollectPotCommand;
import controllers.commands.greenhouse.GrowPotCommand;
import controllers.commands.greenhouse.PlantPotCommand;
import controllers.commands.greenhouse.ShowGreenhouseCommand;
import controllers.commands.menu.EnterMenuCommand;
import controllers.commands.authentication.LogoutCommand;
import controllers.commands.menu.ExitMenuCommand;
import controllers.commands.news.NewsViewType;
import controllers.commands.news.ShowNewsCommand;
import controllers.commands.playmenu.CheatAddCommand;
import controllers.commands.playmenu.EnterChapterCommand;
import controllers.commands.playmenu.EnterOtherMenus;
import controllers.commands.playmenu.ShowWalletCommand;
import controllers.commands.profileandsettings.*;
import controllers.commands.menu.ShowCurrentMenuCommand;
import controllers.commands.seedselection.*;
import controllers.commands.shopandeconomy.BuyShopItemCommand;
import controllers.commands.shopandeconomy.ShowShopCommand;
import models.game.GameSession;
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
    private GameSession gameSession;

    private boolean running;

    private final AllMenuRenderer allMenuRenderer = new AllMenuRenderer();
    private final CollectionMenuRenderer collectionMenuRenderer = new CollectionMenuRenderer();
    private final PlayMenuRenderer playMenuRenderer = new PlayMenuRenderer();
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
            case MAIN_MENU :
                if(MainMenuRegex.LOG_OUT.matches(input)) {logout(); return;}
                break;
            case SETTINGS_MENU :
                if(SettingMenuRegex.CHANGE_DL.matches(input)){
                    changeDL(SettingMenuRegex.CHANGE_DL.getGroup(input , "dl")); return;}
                break;
            case PROFILE_MENU :
                if (handleProfileMenuExecute(input)) return;
                break;
            case PLAY_MENU :
                if (handlePlayMenuExecute(input)) return;
                break;
            case SHOP_MENU :
                if (handleShopMenuExecute(input)) return;
                break;
            case SIGNUP_MENU :
                if (SignUpMenuRegex.SIGN_UP.matches(input)) {
                    new RegisterCommand(input, signUpMenuRenderer).execute();
                    return;
                }
                break;
            case LOGIN_MENU:
                if (LoginMenuRegex.LOGIN.matches(input)) {
                    new LoginCommand(input, appSession, loginMenuRenderer).execute();
                    return;
                } else if (LoginMenuRegex.FORGET_PASSWORD.matches(input)) {
                    new ForgetPasswordCommand(input, appSession, loginMenuRenderer).execute();
                    return;
                }
                break;
            case GREENHOUSE_MENU:
                if (GreenHouseMenuRegex.ENTER_SHOP.matches(input)) {
                    enterShop();
                    return;
                } else if (GreenHouseMenuRegex.PLANT.matches(input)) {
                    plantPot(input);
                    return;
                } else if (GreenHouseMenuRegex.SHOW_STATUS.matches(input)){
                    new ShowGreenhouseCommand(appSession.getCurrentUser().getProfile().getMyGreenHouse(),
                            greenhouseRenderer).execute();
                    return;
                } else if (GreenHouseMenuRegex.COLLECT.matches(input)) {
                    collectPot(input);
                    return;
                } else if (GreenHouseMenuRegex.GROW.matches(input)) {
                    growPlant(input);
                    return;
                }
                break;
            case NEWS_MENU:
                if(handleNewsMenuExecute(input)) return;
                break;
            case COLLECTION_MENU:
                if(handleCollectionMenuExecute(input)) return;
                break;
            case PLANTS_MENU:
                if(handlePlantMenuExecute(input)) return;
                break;
            }


        allMenuRenderer.invalidCommand();
    }

    private boolean handlePlantMenuExecute(String input){
        if(SeedSelectionRegex.SHOW_ALL_PLANTS.matches(input)){
            new ShowSeedsCommand(gameSession, true, plantMenuRenderer).execute();
            return true;
        }
        else if(SeedSelectionRegex.SHOW_AVAILABLE_PLANTS.matches(input)){
            new ShowSeedsCommand(gameSession, false, plantMenuRenderer).execute();
            return true;
        }
        else if (SeedSelectionRegex.ADD_PLANT.matches(input)){
            String plantName = SeedSelectionRegex.ADD_PLANT.getGroup(input, "type");
            new ToggleSeedCommand(ToggleAction.ADD, plantName , gameSession, plantMenuRenderer).execute();
            return true;
        }
        else if(SeedSelectionRegex.REMOVE_PLANT.matches(input)){
            String plantName = SeedSelectionRegex.REMOVE_PLANT.getGroup(input, "type");
            new ToggleSeedCommand(ToggleAction.REMOVE , plantName , gameSession, plantMenuRenderer).execute();
            return true;
        }
        else if(SeedSelectionRegex.BOOST_PLANT.matches(input)){
            String plantName = SeedSelectionRegex.BOOST_PLANT.getGroup(input, "type");
            new BoostSeedCommand(plantName , appSession, plantMenuRenderer).execute();
            return true;
        }
        else if(SeedSelectionRegex.START_GAME.matches(input)){
            new StartLevelCommand(gameSession , appSession).execute();
            return true;
        }
        return false;
    }

    private boolean handleCollectionMenuExecute(String input){
        User user = appSession.getCurrentUser();
        if(CollectionMenuRegex.SHOW_PLANTS.matches(input)){
            new ShowCollectionListCommand(ShowListType.PLANTS,user, collectionMenuRenderer).execute();
            return true;
        }
        else if(CollectionMenuRegex.SHOW_ALL_PLANTS.matches(input)){
            new ShowCollectionListCommand(ShowListType.ALL_PLANTS, user, collectionMenuRenderer).execute();
            return true;
        }
        else if(CollectionMenuRegex.SHOW_ZOMBIES.matches(input)){
            new ShowCollectionListCommand(ShowListType.ZOMBIES, user, collectionMenuRenderer).execute();
            return true;
        }
        else if(CollectionMenuRegex.SHOW_ALL_ZOMBIES.matches(input)){
            new ShowCollectionListCommand(ShowListType.ALL_ZOMBIES, user, collectionMenuRenderer).execute();
            return true;
        }
        else if(CollectionMenuRegex.SHOW_ZOMBIE_DETAIL.matches(input)){
            String name = CollectionMenuRegex.SHOW_ZOMBIE_DETAIL.getGroup(input,"zombieName");
            new ShowEntityDetailsCommand(ShowListType.ZOMBIES, name, collectionMenuRenderer).execute();
            return true;
        }
        else if(CollectionMenuRegex.SHOW_PLANT_DETAIL.matches(input)){
            String name = CollectionMenuRegex.SHOW_PLANT_DETAIL.getGroup(input, "plantName");
            new ShowEntityDetailsCommand(ShowListType.PLANTS , name , collectionMenuRenderer).execute();
            return true;
        }
        else if(CollectionMenuRegex.PURCHASE_PLANT.matches(input)){
            String name = CollectionMenuRegex.PURCHASE_PLANT.getGroup(input , "plantName");
            new UnlockPlantCommand(name, user, collectionMenuRenderer).execute();
            return true;
        }
        else if(CollectionMenuRegex.UPGRADE_PLANT.matches(input)){
            String name = CollectionMenuRegex.UPGRADE_PLANT.getGroup(input, "plantName");
            new UpgradePlantCommand(name , user, collectionMenuRenderer).execute();
            return true;
        }
        return false;
    }

    private boolean handleNewsMenuExecute(String input){
        User user = appSession.getCurrentUser();
        if(NewsMenuRegex.SHOW_UNREAD.matches(input)){
            new ShowNewsCommand(user, NewsViewType.UNREAD, newsMenuRenderer).execute();
            return true;
        }
        else if(NewsMenuRegex.SHOW_ALL.matches(input)){
            new ShowNewsCommand(user,NewsViewType.ALL, newsMenuRenderer).execute();
            return true;
        }
        return false;
    }

    private boolean handleShopMenuExecute(String input) {
        Shop shop = appSession.getShop();
        if(ShopMenuRegex.SHOP_LIST.matches(input)){
            new ShowShopCommand("list", shop, shopRenderer).execute();
            return true;
        }
        else if(ShopMenuRegex.SHOP_DAILY.matches(input)){
            new ShowShopCommand("daily", shop, shopRenderer).execute();
            return true;
        }
        else if(ShopMenuRegex.BUY.matches(input)){
            new BuyShopItemCommand(Integer.parseInt(ShopMenuRegex.BUY.getGroup(input , "id")) , appSession.getShop(),
                    Integer.parseInt(ShopMenuRegex.BUY.getGroup(input , "number")) ,
                    ShopMenuRegex.BUY.getGroup(input , "plantType"),
                    appSession.getCurrentUser().getProfile(), shopRenderer).execute();
            return true;
        }
        return false;
    }

    private boolean handlePlayMenuExecute(String input) {
        if(PlayMenuRegex.ENTER_CHAPTER.matches(input)){
            new EnterChapterCommand(PlayMenuRegex.ENTER_CHAPTER.getGroup(input , "chapter") ,
                    appSession.getCurrentUser().getProfile(), playMenuRenderer).execute();
            return true;
        }
        else if(PlayMenuRegex.ENTER_GREENHOUSE.matches(input)){
            new EnterOtherMenus(MenuType.GREENHOUSE_MENU , appSession, playMenuRenderer).execute();
            return true;
        }
        else if(PlayMenuRegex.ENTER_TRAVEL_LOG.matches(input)){
            new EnterOtherMenus(MenuType.TRAVEL_LOG_MENU , appSession, playMenuRenderer).execute();
            return true;
        }
        else if(PlayMenuRegex.ENTER_LEADERBOARD.matches(input)){
            new EnterOtherMenus(MenuType.LEADERBOARD , appSession, playMenuRenderer).execute();
            return true;
        }
        else if(PlayMenuRegex.SHOW_COINS.matches(input)){
            new ShowWalletCommand(appSession.getCurrentUser().getProfile() , Currency.COIN, playMenuRenderer).execute();
            return true;
        }
        else if(PlayMenuRegex.SHOW_GEMS.matches(input)){
            new ShowWalletCommand(appSession.getCurrentUser().getProfile() , Currency.GEM, playMenuRenderer).execute();
            return true;
        }
        else if(PlayMenuRegex.CHEAT_CODE.matches(input)){
            new CheatAddCommand(PlayMenuRegex.CHEAT_CODE.getGroup(input , "currency") ,
                    Integer.parseInt(PlayMenuRegex.CHEAT_CODE.getGroup(input , "n")) ,
                    appSession.getCurrentUser().getProfile(), playMenuRenderer).execute();
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
        else if(ProfileMenuRegex.CHANGE_PASS.matches(input)) {
            new ProfileCommands(user, EditAction.PASSWORD ,
                    ProfileMenuRegex.CHANGE_PASS.getGroup(input , "newP") ,
                    ProfileMenuRegex.CHANGE_PASS.getGroup(input , "oldP"), profileMenuRenderer).execute();
            return true;
        }
        else if(ProfileMenuRegex.SHOW_INFO.matches(input)) {
            new ShowProfileCommand(user, profileMenuRenderer).execute();
            return true;
        }
        return false;
    }

    private void changeDL(String dl) {
        ChangeDifficultyCommand command = new ChangeDifficultyCommand(appSession.getCurrentUser()
                , Integer.parseInt(dl), settingMenuRenderer);
        command.execute();
    }

    private void logout() {
        LogoutCommand command = new LogoutCommand(appSession, mainMenuRenderer);
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

    private void enterShop(){
        appSession.setCurrentMenu(MenuType.SHOP_MENU);
    }

    private void plantPot(String input){
        int potX = Integer.parseInt(GreenHouseMenuRegex.PLANT.getGroup(input, "x"));
        int potY = Integer.parseInt(GreenHouseMenuRegex.PLANT.getGroup(input, "y"));

        new PlantPotCommand(potX, potY, greenhouseRenderer, appSession).execute();
    }

    private void collectPot(String input){
        int potX = Integer.parseInt(GreenHouseMenuRegex.COLLECT.getGroup(input, "x"));
        int potY = Integer.parseInt(GreenHouseMenuRegex.COLLECT.getGroup(input, "y"));

        new CollectPotCommand(appSession, greenhouseRenderer, potX, potY).execute();
    }

    private void growPlant(String input){
        int potX  = Integer.parseInt(GreenHouseMenuRegex.GROW.getGroup(input, "x"));
        int potY =  Integer.parseInt(GreenHouseMenuRegex.GROW.getGroup(input, "y"));

        new GrowPotCommand(greenhouseRenderer, appSession, potX, potY).execute();
    }

}

