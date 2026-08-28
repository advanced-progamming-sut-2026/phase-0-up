package server.view;

import models.game.GameSession;
import server.ClientSession;
import utils.Result;
import views.Renderers;
import views.renderers.CurrencyRenderer;
import views.renderers.InGameRenderer;
import views.renderers.MapRenderer;
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
import views.renderers.ShopRenderer;

import java.util.function.Consumer;

// The server's View.
//
// GameEngine reports everything the lawn does through an InGameRenderer, and it has never cared which
// one -- the terminal prints, the GUI raises a toast. This third one puts the sentence on the wire, so
// both players read the same commentary the single-player game shows, written once, in the model.
// That is the entire reason the server can run the real engine at all.
//
// ## Two audiences, and the rule that separates them
//
// A Result produced WHILE a player's command is being dispatched belongs to that player: "Not enough
// sun for a Peashooter" is an answer to something one person clicked, and broadcasting it would tell
// their opponent what they tried and could not afford. A Result produced during the TICK belongs to
// both: a zombie eating a plant is a fact about the shared board.
//
// So the runner calls directTo(session) around a dispatch and directTo(null) for the tick, and this
// class routes accordingly. Nothing about the Commands changes.
//
// ## The two banners this deliberately swallows
//
// GameEngine.announceOutcome hardcodes the two spec-verbatim end-of-level sentences, and exactly ONE
// of the two players should ever see each of them -- the mode's win condition is written from the
// zombie player's seat, so the plant player winning arrives here as "The zombie ate your brain;
// LOSER!!!". Both are filtered out and each client renders its own from MatchOver.winner.
public final class RelayRenderers implements Renderers {

    // Matched on exactly, because these two strings are quoted verbatim from the project spec and must
    // never be reworded. If either is ever edited in GameEngine, this filter stops matching and both
    // players start seeing both banners -- which is why RelayRenderersTest asserts the pairing rather
    // than trusting the constants to stay in step.
    public static final String ZOMBIE_VICTORY_BANNER =
            "Dear humanz, zis is not done yet; we will come back to eat your brainz, humanz.";
    public static final String PLANT_DEFEAT_BANNER = "The zombie ate your brain; LOSER!!!";

    private final Consumer<Result> broadcast;
    private final InGameRenderer inGame = this::report;
    private final MapRenderer map = new SilentMapRenderer();

    // Whose command is being dispatched right now, or null during a tick. Written and read only by the
    // match's tick thread -- the same thread that owns the GameSession -- so it needs no synchronising.
    private ClientSession addressee;
    private Consumer<Result> privateSink;

    public RelayRenderers(Consumer<Result> broadcast) {
        this.broadcast = broadcast;
    }

    // Everything reported until the matching directTo(null) goes to this player alone.
    public void directTo(ClientSession session, Consumer<Result> sink) {
        this.addressee = session;
        this.privateSink = sink;
    }

    public void broadcastAgain() {
        this.addressee = null;
        this.privateSink = null;
    }

    private void report(Result result) {
        if (result == null || result.message() == null || result.message().isBlank()) {
            return;
        }
        if (isOutcomeBanner(result.message())) {
            return;
        }
        if (addressee != null && privateSink != null) {
            privateSink.accept(result);
            return;
        }
        broadcast.accept(result);
    }

    public static boolean isOutcomeBanner(String message) {
        return ZOMBIE_VICTORY_BANNER.equals(message) || PLANT_DEFEAT_BANNER.equals(message);
    }

    @Override
    public InGameRenderer inGame() {
        return inGame;
    }

    @Override
    public MapRenderer map() {
        return map;
    }

    // "show map" is a terminal command. The graphical clients draw the board continuously from their
    // own mirror, so there is nothing for the server to render on request -- but the command still
    // parses, so this has to exist and do nothing rather than not exist and throw.
    private static final class SilentMapRenderer implements MapRenderer {
        @Override
        public void renderAllTheMap(GameSession activeSession) {
        }

        @Override
        public void renderGameSession(GameSession activeSession) {
        }
    }

    // ---- menus: the server has none --------------------------------------------------------------
    //
    // Every accessor below throws rather than returning null. The server runs exactly one thing -- a
    // match -- and reaches the menu layer only if something has gone wrong upstream; an exception names
    // the renderer at the call site, where a null would surface as an NPE several frames later in code
    // that has no idea a server is involved.

    private static <T> T noMenus(String which) {
        throw new UnsupportedOperationException(
                "the server has no " + which + " -- menus live in the clients");
    }

    @Override
    public CurrencyRenderer currency() {
        return noMenus("currency renderer");
    }

    @Override
    public ShopRenderer shop() {
        return noMenus("shop");
    }

    @Override
    public AllMenuRenderer allMenu() {
        return noMenus("menu renderer");
    }

    @Override
    public MainMenuRenderer mainMenu() {
        return noMenus("main menu");
    }

    @Override
    public LoginMenuRenderer loginMenu() {
        return noMenus("login menu");
    }

    @Override
    public SignUpMenuRenderer signUpMenu() {
        return noMenus("sign-up menu");
    }

    @Override
    public SettingMenuRenderer settingMenu() {
        return noMenus("settings menu");
    }

    @Override
    public PlayMenuRenderer playMenu() {
        return noMenus("play menu");
    }

    @Override
    public ProfileMenuRenderer profileMenu() {
        return noMenus("profile menu");
    }

    @Override
    public GreenhouseRenderer greenhouse() {
        return noMenus("greenhouse");
    }

    @Override
    public NewsMenuRenderer newsMenu() {
        return noMenus("news menu");
    }

    @Override
    public TravelLogRenderer travelLog() {
        return noMenus("travel log");
    }

    @Override
    public LeaderboardRenderer leaderboard() {
        return noMenus("leaderboard renderer");
    }

    @Override
    public PlantMenuRenderer plantMenu() {
        return noMenus("plant menu");
    }

    @Override
    public CollectionMenuRenderer collectionMenu() {
        return noMenus("collection menu");
    }
}
