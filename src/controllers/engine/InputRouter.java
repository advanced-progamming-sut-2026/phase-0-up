package controllers.engine;


import controllers.commands.menu.EnterMenuCommand;
import controllers.commands.authentication.LogoutCommand;
import controllers.commands.menu.ExitMenuCommand;
import controllers.commands.profileandsettings.ChangeDifficultyCommand;
import controllers.commands.menu.ShowCurrentMenuCommand;
import models.user.AppSession;
import utils.regex.AllMenuRegex;
import utils.regex.MainMenuRegex;
import utils.regex.SettingMenuRegex;
import views.InputHandler;
import views.renderers.*;
import views.renderers.MenuRenderer.*;

public class InputRouter {
    private final AppSession appSession;

    private boolean running;

    private final AllMenuRenderer allMenuRenderer = new AllMenuRenderer();
    private final CollectionMenuRenderer collectionMenuRenderer = new CollectionMenuRenderer();
    private final GameMenuRenderer gameMenuRenderer = new GameMenuRenderer();
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
        if (AllMenuRegex.EXIT_MENU.matches(input)) exitMenu();
        else if (AllMenuRegex.ENTER_MENU.matches(input)) enterMenu(input);
        else if (AllMenuRegex.SHOW_CURRENT.matches(input)) new ShowCurrentMenuCommand(appSession, allMenuRenderer).execute();
        switch (appSession.getCurrentMenu()){
            case MAIN_MENU -> {
                if(MainMenuRegex.LOG_OUT.matches(input)) logout();}
            case SETTINGS_MENU -> {
                if(SettingMenuRegex.CHANGE_DL.matches(input))
                    changeDL(SettingMenuRegex.CHANGE_DL.getGroup(input , "dl"));}
        }
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

