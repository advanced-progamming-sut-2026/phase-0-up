package views.console;

import views.Renderers;
import views.console.menu.ConsoleAllMenuRenderer;
import views.console.menu.ConsoleCollectionMenuRenderer;
import views.console.menu.ConsoleGreenhouseRenderer;
import views.console.menu.ConsoleLeaderboardRenderer;
import views.console.menu.ConsoleLoginMenuRenderer;
import views.console.menu.ConsoleMainMenuRenderer;
import views.console.menu.ConsoleNewsMenuRenderer;
import views.console.menu.ConsolePlantMenuRenderer;
import views.console.menu.ConsolePlayMenuRenderer;
import views.console.menu.ConsoleProfileMenuRenderer;
import views.console.menu.ConsoleSettingMenuRenderer;
import views.console.menu.ConsoleSignUpMenuRenderer;
import views.console.menu.ConsoleTravelLogRenderer;
import views.renderers.CurrencyRenderer;
import views.renderers.InGameRenderer;
import views.renderers.MapRenderer;
import views.renderers.ShopRenderer;
import views.renderers.MenuRenderer.AllMenuRenderer;
import views.renderers.MenuRenderer.CollectionMenuRenderer;
import views.renderers.MenuRenderer.GreenhouseRenderer;
import views.renderers.MenuRenderer.LeaderboardRenderer;
import views.renderers.MenuRenderer.LoginMenuRenderer;
import views.renderers.MenuRenderer.MainMenuRenderer;
import views.renderers.MenuRenderer.NewsMenuRenderer;
import views.renderers.MenuRenderer.PlantMenuRenderer;
import views.renderers.MenuRenderer.PlayMenuRenderer;
import views.renderers.MenuRenderer.ProfileMenuRenderer;
import views.renderers.MenuRenderer.SettingMenuRenderer;
import views.renderers.MenuRenderer.SignUpMenuRenderer;
import views.renderers.MenuRenderer.TravelLogRenderer;

// The terminal View: every renderer prints to stdout.
//
// Instances are built once and shared, because they are stateless. Nothing here touches LibGDX, which
// is what keeps `gradlew run` usable as a headless regression harness for the graphical build.
public final class ConsoleRenderers implements Renderers {

    private final InGameRenderer inGame = new ConsoleInGameRenderer();
    private final MapRenderer map = new ConsoleMapRenderer();
    private final CurrencyRenderer currency = new ConsoleCurrencyRenderer();
    private final ShopRenderer shop = new ConsoleShopRenderer();

    private final AllMenuRenderer allMenu = new ConsoleAllMenuRenderer();
    private final MainMenuRenderer mainMenu = new ConsoleMainMenuRenderer();
    private final LoginMenuRenderer loginMenu = new ConsoleLoginMenuRenderer();
    private final SignUpMenuRenderer signUpMenu = new ConsoleSignUpMenuRenderer();
    private final SettingMenuRenderer settingMenu = new ConsoleSettingMenuRenderer();
    private final PlayMenuRenderer playMenu = new ConsolePlayMenuRenderer();
    private final ProfileMenuRenderer profileMenu = new ConsoleProfileMenuRenderer();
    private final GreenhouseRenderer greenhouse = new ConsoleGreenhouseRenderer();
    private final NewsMenuRenderer newsMenu = new ConsoleNewsMenuRenderer();
    private final TravelLogRenderer travelLog = new ConsoleTravelLogRenderer();
    private final LeaderboardRenderer leaderboard = new ConsoleLeaderboardRenderer();
    private final PlantMenuRenderer plantMenu = new ConsolePlantMenuRenderer();
    private final CollectionMenuRenderer collectionMenu = new ConsoleCollectionMenuRenderer();

    @Override
    public InGameRenderer inGame() {
        return inGame;
    }

    @Override
    public MapRenderer map() {
        return map;
    }

    @Override
    public CurrencyRenderer currency() {
        return currency;
    }

    @Override
    public ShopRenderer shop() {
        return shop;
    }

    @Override
    public AllMenuRenderer allMenu() {
        return allMenu;
    }

    @Override
    public MainMenuRenderer mainMenu() {
        return mainMenu;
    }

    @Override
    public LoginMenuRenderer loginMenu() {
        return loginMenu;
    }

    @Override
    public SignUpMenuRenderer signUpMenu() {
        return signUpMenu;
    }

    @Override
    public SettingMenuRenderer settingMenu() {
        return settingMenu;
    }

    @Override
    public PlayMenuRenderer playMenu() {
        return playMenu;
    }

    @Override
    public ProfileMenuRenderer profileMenu() {
        return profileMenu;
    }

    @Override
    public GreenhouseRenderer greenhouse() {
        return greenhouse;
    }

    @Override
    public NewsMenuRenderer newsMenu() {
        return newsMenu;
    }

    @Override
    public TravelLogRenderer travelLog() {
        return travelLog;
    }

    @Override
    public LeaderboardRenderer leaderboard() {
        return leaderboard;
    }

    @Override
    public PlantMenuRenderer plantMenu() {
        return plantMenu;
    }

    @Override
    public CollectionMenuRenderer collectionMenu() {
        return collectionMenu;
    }
}
