package views.gdx.core;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.ScreenUtils;
import controllers.engine.MenuType;
import models.user.AppSession;
import models.user.User;
import utils.gameinitializers.GameInitializer;
import utils.gameinitializers.LevelInitializer;
import utils.storage.DatabaseManager;

// Root of the graphical build -- the LibGDX counterpart to Main.
//
// This is a composition root and nothing else: it loads the game data, restores the session, and owns
// every disposable resource in the GUI. It deliberately holds no game rules. All of those still live
// in models/ and controllers/, exactly where the terminal build left them, which is what lets both
// front ends run the same game.
public class PvZGame extends Game {

    // Design resolution. Every Screen uses a FitViewport against these numbers, so widgets are laid out
    // once in this space and letterboxed to whatever the window actually is -- rather than every screen
    // re-deriving positions from Gdx.graphics.getWidth().
    //
    // This is the UI's coordinate space, NOT the window size, and it is deliberately left at 720p: the
    // HUD is authored against it, and doubling it would halve the on-screen size of every widget and
    // font. The window is what changes -- see WINDOW_WIDTH below. FitViewport scales this space up to
    // fill it, so the HUD stays the same relative size at any resolution.
    public static final float VIRTUAL_WIDTH = 1280f;
    public static final float VIRTUAL_HEIGHT = 720f;

    // Default window size. Same 16:9 shape as the design resolution, so the lawn fills the window with
    // no letterboxing -- a different aspect would put black bars top and bottom.
    public static final int WINDOW_WIDTH = 1920;
    public static final int WINDOW_HEIGHT = 1080;

    private AppSession appSession;
    private Assets assets;
    private AudioManager audio;
    private Toasts toasts;
    private ScreenManager screens;
    private GdxContext context;
    private SmokeHarness smoke;
    private views.Renderers renderers;
    private controllers.engine.InputRouter router;

    @Override
    public void create() {
        smoke = SmokeHarness.fromSystemProperties();
        if (smoke.isActive()) {
            Gdx.app.log("PvZGame", smoke.describe());
        }
        if (DebugFlags.GL_PROFILE) {
            glProfiler = new com.badlogic.gdx.graphics.profiling.GLProfiler(Gdx.graphics);
            glProfiler.enable();
            Gdx.app.log("PvZGame", "GL profiling enabled");
        }
        bootstrapModel();

        assets = new Assets();
        toasts = new Toasts(assets);
        audio = new AudioManager();
        views.gdx.sprite.SpriteRegistry sprites =
                new views.gdx.sprite.SpriteRegistry(assets.pam(), assets.bank(), assets.root());

        // The graphical View. Built here and nowhere else -- this is the counterpart to Main's
        // ConsoleRenderers line, and the only difference between the two builds.
        renderers = new views.gdx.renderers.GdxRenderers(toasts);

        // Now that a View exists, the model's balance hook can be claimed. It is a single static field,
        // so exactly one implementation may hold it; the terminal and graphical builds never share a
        // JVM, so each simply takes it at start-up.
        models.user.Profile.setCurrencyObserver(renderers.currency()::showBalance);

        // The same router the terminal build uses, minus its stdin loop. Menu buttons post the string
        // the player would have typed; startLoop() is never called, so nothing here reads stdin.
        router = new controllers.engine.InputRouter(appSession, renderers);
        context = new GdxContext(this, assets, toasts, sprites, appSession, renderers,
                new views.gdx.bridge.MenuCommands(router));

        runAssetDiagnostics(sprites);

        // The signed-in player's saved volume, applied before the first screen opens so nothing is
        // heard at the default before their own setting takes effect.
        models.user.User signedIn = appSession.getCurrentUser();
        if (signedIn != null && signedIn.getProfile() != null) {
            audio.setVolume(signedIn.getProfile().getVolume());
        }
        // Every button in the game clicks through one line. See ButtonJuice.setClickSound.
        views.gdx.ui.ButtonJuice.setClickSound(() -> audio.play(AudioManager.SFX_BUTTON));

        screens = new ScreenManager(context);
        registerScreens();
        openEntryScreen();
    }

    // The two asset probes. Both are off unless their system property is set, and both exist because
    // the failure they diagnose is silent: an id that does not resolve returns null and the caller
    // quietly falls back, so a typo looks exactly like a layout bug.
    private void runAssetDiagnostics(views.gdx.sprite.SpriteRegistry sprites) {
        // Diagnostic for building visibility maps: -Dpvz.dumpParts=ZombieArmor1,ZombieDefault
        String dump = System.getProperty("pvz.dumpParts");
        if (dump != null && !dump.isBlank()) {
            for (String name : dump.split("\\s*,\\s*")) {
                views.gdx.sprite.EntitySprite s = sprites.get(name);
                for (String clip : sprites.clipsOf(name)) {
                    Gdx.app.log("Bounds", name + " [" + clip + "] = " + s.bounds(clip));
                }
                // Which clip anything outside the lawn will actually rest on, and how big that clip's
                // box is. Both matter together: a seed card and an almanac tile scale the art to fit
                // this box, so an entity that rests on the wrong clip is not merely in the wrong pose --
                // it is drawn at the wrong SIZE. Newspaper Zombie rested on "walk" (a box of 808x378
                // against idle_newspaper's 175x204) and appeared less than half the size of its
                // neighbours; nothing in the old output said so.
                String resting = views.gdx.sprite.PlantStages.restingClip(s);
                Gdx.app.log("Resting", name + " -> " + resting + "  " + s.bounds(resting));
                reportVisibleBounds(name, s, resting);
                reportGroundSwatch(name, s);
                // The part list is the half this flag always claimed to print and never did. It is the
                // useful half: when an entity's states are layers rather than clips -- a zombie's cone,
                // a Wall-nut's cracked shell -- the part names are what a visibility map switches on.
                Gdx.app.log("Parts", name + " = " + sprites.partNames(name));
            }
        }
        reportProbedRegions();
    }

    // The full clip box against the box of only what is drawn, and which switched-off part accounts for
    // the difference.
    //
    // On the shared zombie body the two differ by a whole helmet -- a bare Browncoat measures 169x250
    // against a true 131x197 -- and since every card scales its art to FIT that box, the gap is drawn as
    // a smaller zombie sitting low under a hat's worth of nothing. Naming the culprit part is what turns
    // "this card looks off" into a fact.
    private void reportVisibleBounds(String name, views.gdx.sprite.EntitySprite sprite, String clip) {
        java.util.Set<String> toggles = views.gdx.sprite.ArmorVisibility.togglePartsOf(sprite);
        com.badlogic.gdx.math.Rectangle full = sprite.bounds(clip);
        com.badlogic.gdx.math.Rectangle drawn = sprite.visibleBounds(clip, toggles);
        Gdx.app.log("Visible", name + ": full " + full + "  drawn " + drawn);
        if (full == null) {
            return;
        }
        for (String part : toggles) {
            com.badlogic.gdx.math.Rectangle box = sprite.partBounds(clip, part);
            // libPVZ reports these in the .PAM's own y-down space, so the SMALLEST y is the topmost edge.
            if (box != null && box.y <= full.y + 0.01f) {
                Gdx.app.log("Visible", "   ^ " + part + " is what makes it that tall: " + box);
            }
        }
    }

    // The path of the ground_swatch marker through the walk cycle, which is what foot-planting reads.
    private void reportGroundSwatch(String name, views.gdx.sprite.EntitySprite sprite) {
        com.badlogic.gdx.math.Rectangle[] frames =
                sprite.partBoundsByFrame("walk", views.gdx.sprite.WalkCycle.GROUND_SWATCH);
        if (frames == null) {
            Gdx.app.log("Swatch", name + ": walk has no " + views.gdx.sprite.WalkCycle.GROUND_SWATCH);
            return;
        }
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < frames.length; i++) {
            if (frames[i] == null) {
                path.append(i).append(":null  ");
                continue;
            }
            if (i % 8 == 0 || i == frames.length - 1) {
                path.append(i).append(':').append(Math.round(frames[i].x)).append("  ");
            }
        }
        Gdx.app.log("Swatch", name + ": walk " + frames.length + " frames, duration "
                + sprite.clipDuration("walk") + "s, x path -> " + path);
    }

    private void reportProbedRegions() {
        // Diagnostic: -Dpvz.probeRegions=ID1,ID2 reports whether each RESOURCES.json image id resolves
        // to a real atlas region, and how big it is. An id that does not resolve returns null and the
        // caller silently falls back to a placeholder, so without this a typo looks like a layout bug.
        String probe = System.getProperty("pvz.probeRegions");
        if (probe != null && !probe.isBlank()) {
            for (String id : probe.split("\\s*,\\s*")) {
                com.badlogic.gdx.graphics.g2d.TextureRegion region = assets.region(id);
                Gdx.app.log("Region", (region == null ? "MISSING  " : "ok       ") + id
                        + (region == null ? "" : "  " + region.getRegionWidth()
                                + "x" + region.getRegionHeight()));
            }
        }
    }

    // Where the player lands.
    //
    // The default is the menus: sync() reads AppSession's current menu -- SIGNUP_MENU for a fresh
    // install, MAIN_MENU for an auto-logged-in one -- and shows the screen registered against it, so
    // the model decides the entry point exactly as it decides every later transition.
    //
    // The two overrides are development routes:
    //   -Dpvz.screen=game     straight onto a pre-built lawn via DevBoot, skipping level choice
    //   -Dpvz.screen=sprites  the Phase 0 asset harness
    private void openEntryScreen() {
        String entry = System.getProperty("pvz.screen", "").trim();
        if ("sprites".equalsIgnoreCase(entry)) {
            // Same trap the "game" branch below documents, and this branch was still falling into it:
            // showDetached leaves ScreenManager with nothing displayed, so the very next sync() sees
            // the session sitting in MAIN_MENU and swaps the menu straight back over the harness. The
            // sprite viewer belongs to no menu at all, so the session is pointed at none -- sync()
            // returns early on a null target and leaves the harness alone.
            appSession.setCurrentMenu(null);
            screens.showDetached(new views.gdx.screens.SpriteSmokeScreen(context));
        } else if ("game".equalsIgnoreCase(entry)) {
            // The session's menu has to move too, not just the screen. showDetached leaves
            // ScreenManager with nothing displayed, so the next sync() sees the session still sitting
            // in MAIN_MENU and swaps the lawn straight back out from under this -- which is exactly
            // what this flag stopped doing once the menus became the default entry point.
            appSession.setCurrentMenu(MenuType.IN_GAME);
            screens.showDetached(new views.gdx.screens.GameScreen(
                    context, views.gdx.screens.DevBoot.start(appSession, renderers)));
        } else {
            openStartingMenu();
            screens.sync();
        }
    }

    // -Dpvz.menu=<name> opens straight onto one menu, using the same names the CLI takes
    // ("main", "profile", "settings", "play"). Every Phase 4 screen is otherwise several clicks and a
    // sign-in away, which makes checking one of them by eye far more work than fixing it.
    //
    // Menus past the sign-in wall need somebody signed in, so this borrows the first saved account
    // rather than pretending. It changes nothing a real sign-in would not: the same Profile repairs
    // run, and no rule is bypassed -- this is a route in, not a permission.
    private void openStartingMenu() {
        String wanted = System.getProperty("pvz.menu", "").trim();
        if (wanted.isEmpty()) {
            return;
        }
        MenuType menu = MenuType.fromName(wanted);
        if (menu == null) {
            Gdx.app.error("PvZGame", "-Dpvz.menu=" + wanted + " is not a menu name");
            return;
        }
        if (appSession.getCurrentUser() == null) {
            signInFirstSavedUser();
        }
        if (menu == MenuType.PLANTS_MENU || menu == MenuType.IN_GAME) {
            openFirstLevel(menu);
        } else {
            appSession.setCurrentMenu(menu);
        }
        Gdx.app.log("PvZGame", "opening menu: " + appSession.getCurrentMenu());
    }

    // Seed selection and the lawn both need a level already chosen, so this walks the real route to one
    // -- the same two commands the Adventure screen's buttons post -- rather than assembling a
    // GameSession by hand. Anything the real flow does that a hand-built session would skip is exactly
    // what this shortcut must not hide.
    private void openFirstLevel(MenuType menu) {
        // Which one, from the same two properties DevBoot takes, so -Dpvz.menu=plants and
        // -Dpvz.screen=game are aimed the same way. It defaults to 1-1 and matters because every
        // special mode lives on a `-3`: seed selection under Locked Plants cannot be reached at all
        // without it, and that is the screen the mode's slot rules are about.
        int chapter = intProperty("pvz.devChapter", 1);
        int level = intProperty("pvz.devLevel", 1);

        // From the play menu, because the router dispatches on the menu the session is CURRENTLY in --
        // "menu enter chapter" is a play-menu command and is not recognised anywhere else.
        appSession.setCurrentMenu(MenuType.PLAY_MENU);
        router.submit("menu enter chapter -c " + chapter);
        router.submit("level -l " + level);
        if (appSession.getCurrentGameSession() == null) {
            Gdx.app.error("PvZGame", "-Dpvz.menu=" + menu.getMenuName()
                    + ": could not open chapter " + chapter + " level " + level);
            return;
        }
        appSession.setCurrentMenu(menu);
    }

    private static int intProperty(String key, int fallback) {
        try {
            return Integer.parseInt(System.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void signInFirstSavedUser() {
        java.util.Collection<User> users = DatabaseManager.getInstance().getAllUsers();
        if (users == null || users.isEmpty()) {
            Gdx.app.log("PvZGame", "-Dpvz.menu: no saved accounts, staying signed out");
            return;
        }
        User user = users.iterator().next();
        appSession.setCurrentUser(user);
        user.getProfile().ensureStartingPlants();
        LevelInitializer.attachCampaign(user.getProfile());
        Gdx.app.log("PvZGame", "-Dpvz.menu: signed in as " + user.getUsername());
    }

    // Screens are registered against the MenuType the existing Commands already move between, so the
    // model keeps driving navigation: a button runs a Command, the Command moves the session, and
    // ScreenManager.sync() catches up on the next frame. Filled in as Phase 4 lands each screen.
    private void registerScreens() {
        screens.register(MenuType.SIGNUP_MENU, views.gdx.screens.RegisterScreen::new);
        screens.register(MenuType.LOGIN_MENU, views.gdx.screens.LoginScreen::new);
        screens.register(MenuType.MAIN_MENU, views.gdx.screens.MainMenuScreen::new);
        screens.register(MenuType.PROFILE_MENU, views.gdx.screens.ProfileScreen::new);
        screens.register(MenuType.SETTINGS_MENU, views.gdx.screens.SettingsScreen::new);
        screens.register(MenuType.PLAY_MENU, views.gdx.screens.AdventureScreen::new);
        screens.register(MenuType.PLANTS_MENU, views.gdx.screens.SeedSelectionScreen::new);
        screens.register(MenuType.IN_GAME, views.gdx.screens.GameScreen::new);
        screens.register(MenuType.NEWS_MENU, views.gdx.screens.NewsScreen::new);
        screens.register(MenuType.LEADERBOARD, views.gdx.screens.LeaderboardScreen::new);
        screens.register(MenuType.COLLECTION_MENU, views.gdx.screens.CollectionScreen::new);
        screens.register(MenuType.SHOP_MENU, views.gdx.screens.StoreScreen::new);
        screens.register(MenuType.GREENHOUSE_MENU, views.gdx.screens.GreenhouseScreen::new);
        screens.register(MenuType.TRAVEL_LOG_MENU, views.gdx.screens.TravelLogScreen::new);
    }

    // Mirrors Main.main's startup, minus the console wiring.
    //
    // Order matters and is not obvious: the registries must be populated before anything reads a
    // template, and an auto-logged-in profile needs two repairs that its constructor never ran because
    // Gson deserialises straight past it. Getting this wrong does not fail loudly -- it surfaces much
    // later as every seed reading "locked", or an NPE the first time a level is picked.
    private void bootstrapModel() {
        new GameInitializer().loadAllData();

        DatabaseManager db = DatabaseManager.getInstance();
        appSession = new AppSession();

        User autoLoggedInUser = db.getLoggedInUser();
        if (autoLoggedInUser != null) {
            Gdx.app.log("PvZGame", "Auto-logging user: " + autoLoggedInUser.getUsername());
            appSession.setCurrentUser(autoLoggedInUser);
            // A saved profile is deserialized past the constructor, so re-grant the starter plants it
            // would otherwise be missing (every seed would read as "locked" without this).
            autoLoggedInUser.getProfile().ensureStartingPlants();
            // Chapters/levels are never persisted, so an auto-logged-in user needs the same campaign
            // rebuild LoginCommand does -- without it currentChapter stays null and picking a level NPEs.
            LevelInitializer.attachCampaign(autoLoggedInUser.getProfile());
            appSession.setCurrentMenu(MenuType.MAIN_MENU);
        }

        // Profile.setCurrencyObserver is wired in create(), not here: it needs the View, and the View
        // needs Assets, which this method runs before.
    }

    // The screen stack is installed in T0.5; until then this just proves the window and the render loop
    // are alive.
    // Counts draw calls / texture binds / shader switches. Enabled by -Dpvz.glProfile=1.
    private com.badlogic.gdx.graphics.profiling.GLProfiler glProfiler;
    private float glReportTimer;

    @Override
    public void render() {
        // Hands finished background atlas loads to the GL thread. Skipping it does not error -- the
        // textures simply never appear, which is a genuinely confusing way to fail.
        assets.update();

        if (glProfiler != null) {
            reportGlProfile();
        }

        // Catch up with any menu change a Command made since the last frame, before drawing.
        screens.sync();

        ScreenUtils.clear(0.09f, 0.13f, 0.09f, 1f);

        // Deliberately not super.render(): Game.render() hardcodes Gdx.graphics.getDeltaTime(), and
        // smoke runs need a fixed step so screenshots are reproducible (see SmokeHarness.step).
        if (getScreen() != null) {
            getScreen().render(smoke.step(Gdx.graphics.getDeltaTime()));
        }

        // Always last, so notifications sit above whatever the screen drew -- including the game board,
        // which renders through a different camera.
        toasts.render(Gdx.graphics.getDeltaTime());

        smoke.afterFrame();
    }

    // Once a second rather than per frame, and reset each time so the numbers are per-second rates
    // instead of ever-growing totals.
    private void reportGlProfile() {
        glReportTimer += Gdx.graphics.getDeltaTime();
        if (glReportTimer < 1f) {
            return;
        }
        glReportTimer = 0f;
        Gdx.app.log("GL", "fps=" + Gdx.graphics.getFramesPerSecond()
                + " drawCalls=" + glProfiler.getDrawCalls()
                + " textureBinds=" + glProfiler.getTextureBindings()
                + " shaderSwitches=" + glProfiler.getShaderSwitches()
                + " glCalls=" + glProfiler.getCalls());
        glProfiler.reset();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);   // forwards to the active Screen
        toasts.resize(width, height);
    }

    @Override
    public void dispose() {
        // ScreenManager owns the active screen -- deliberately not disposed here as well, or it would
        // be disposed twice.
        if (screens != null) {
            screens.dispose();
        }
        if (toasts != null) {
            toasts.dispose();
        }
        if (audio != null) {
            audio.dispose();
        }
        if (assets != null) {
            assets.dispose();
        }
        super.dispose();
    }

    public GdxContext getContext() {
        return context;
    }

    public AppSession getAppSession() {
        return appSession;
    }

    public Assets getAssets() {
        return assets;
    }

    public AudioManager audio() {
        return audio;
    }
}
