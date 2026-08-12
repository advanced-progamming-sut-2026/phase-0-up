package views;

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

// The whole View, in one injectable object.
//
// This is the seam the two front ends meet at. InputRouter and GameEngine used to construct their
// renderers with `new`, which quietly compiled the terminal into the controller layer: there was no
// point at which a different View could be supplied. They now ask for one of these instead, and the
// only code that knows which implementation exists is the composition root -- Main for the terminal
// build, PvZGame for the graphical one.
//
// Deliberately an interface of accessors rather than a 17-argument constructor or a map keyed by
// Class. Accessors are checked by the compiler (a front end that forgets a renderer does not build),
// and an implementation is free to hand out the same instance every time or build one per call.
public interface Renderers {

    // ---- in-game ----
    InGameRenderer inGame();

    MapRenderer map();

    // ---- economy ----
    CurrencyRenderer currency();

    ShopRenderer shop();

    // ---- menus ----
    AllMenuRenderer allMenu();

    MainMenuRenderer mainMenu();

    LoginMenuRenderer loginMenu();

    SignUpMenuRenderer signUpMenu();

    SettingMenuRenderer settingMenu();

    PlayMenuRenderer playMenu();

    ProfileMenuRenderer profileMenu();

    GreenhouseRenderer greenhouse();

    NewsMenuRenderer newsMenu();

    TravelLogRenderer travelLog();

    LeaderboardRenderer leaderboard();

    PlantMenuRenderer plantMenu();

    CollectionMenuRenderer collectionMenu();
}
