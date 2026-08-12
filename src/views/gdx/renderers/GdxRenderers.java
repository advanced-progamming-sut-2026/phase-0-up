package views.gdx.renderers;

import views.Renderers;
import views.gdx.core.ToastSink;
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

// The graphical View: every renderer reports through the toast overlay.
//
// This is what makes the 57 Commands work unchanged on the lawn. A Command still enforces its rule and
// still produces its sentence; the sentence just arrives as a toast instead of on stdout. Where a
// screen already shows what a renderer would have said -- the seed grid, the almanac, the board itself
// -- the corresponding method is deliberately silent rather than duplicating it, and says so.
//
// Nothing in this package imports com.badlogic.gdx: the renderers talk to ToastSink, so they can be
// built and asserted against without a GL context.
public final class GdxRenderers implements Renderers {

    private final InGameRenderer inGame;
    private final MapRenderer map;
    private final CurrencyRenderer currency;
    private final ShopRenderer shop;

    private final AllMenuRenderer allMenu;
    private final MainMenuRenderer mainMenu;
    private final LoginMenuRenderer loginMenu;
    private final SignUpMenuRenderer signUpMenu;
    private final SettingMenuRenderer settingMenu;
    private final PlayMenuRenderer playMenu;
    private final ProfileMenuRenderer profileMenu;
    private final GreenhouseRenderer greenhouse;
    private final NewsMenuRenderer newsMenu;
    private final TravelLogRenderer travelLog;
    private final LeaderboardRenderer leaderboard;
    private final PlantMenuRenderer plantMenu;
    private final CollectionMenuRenderer collectionMenu;

    public GdxRenderers(ToastSink toasts) {
        this.inGame = new GdxInGameRenderer(toasts);
        this.map = new GdxMapRenderer(toasts);
        this.currency = new GdxCurrencyRenderer(toasts);
        this.shop = new GdxShopRenderer(toasts);

        this.allMenu = new GdxAllMenuRenderer(toasts);
        this.mainMenu = new GdxMainMenuRenderer(toasts);
        this.loginMenu = new GdxLoginMenuRenderer(toasts);
        this.signUpMenu = new GdxSignUpMenuRenderer(toasts);
        this.settingMenu = new GdxSettingMenuRenderer(toasts);
        this.playMenu = new GdxPlayMenuRenderer(toasts);
        this.profileMenu = new GdxProfileMenuRenderer(toasts);
        this.greenhouse = new GdxGreenhouseRenderer(toasts);
        this.newsMenu = new GdxNewsMenuRenderer(toasts);
        this.travelLog = new GdxTravelLogRenderer(toasts);
        this.leaderboard = new GdxLeaderboardRenderer(toasts);
        this.plantMenu = new GdxPlantMenuRenderer(toasts);
        this.collectionMenu = new GdxCollectionMenuRenderer(toasts);
    }

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
