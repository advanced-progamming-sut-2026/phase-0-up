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
    private Toasts toasts;
    private ScreenManager screens;
    private GdxContext context;
    private SmokeHarness smoke;

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
        views.gdx.sprite.SpriteRegistry sprites =
                new views.gdx.sprite.SpriteRegistry(assets.pam(), assets.bank(), assets.root());

        context = new GdxContext(this, assets, toasts, sprites, appSession);

        // Diagnostic for building visibility maps: -Dpvz.dumpParts=ZombieArmor1,ZombieDefault
        String dump = System.getProperty("pvz.dumpParts");
        if (dump != null && !dump.isBlank()) {
            for (String name : dump.split("\\s*,\\s*")) {
                views.gdx.sprite.EntitySprite s = sprites.get(name);
                for (String clip : sprites.clipsOf(name)) {
                    Gdx.app.log("Bounds", name + " [" + clip + "] = " + s.bounds(clip));
                }
                // The part list is the half this flag always claimed to print and never did. It is the
                // useful half: when an entity's states are layers rather than clips -- a zombie's cone,
                // a Wall-nut's cracked shell -- the part names are what a visibility map switches on.
                Gdx.app.log("Parts", name + " = " + sprites.partNames(name));
            }
        }
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

        screens = new ScreenManager(context);
        registerScreens();

        // Phase 1: boot straight onto the lawn. No menus exist yet, so the route in is hard-wired here
        // and replaced at T4.8 by Adventure -> Seed Selection -> Game. -Dpvz.screen=sprites still opens
        // the Phase 0 asset harness.
        if ("sprites".equalsIgnoreCase(System.getProperty("pvz.screen", ""))) {
            screens.showDetached(new views.gdx.screens.SpriteSmokeScreen(context));
        } else {
            screens.showDetached(new views.gdx.screens.GameScreen(
                    context, views.gdx.screens.DevBoot.start(appSession)));
        }
    }

    // Screens are registered against the MenuType the existing Commands already move between, so the
    // model keeps driving navigation. Filled in as Phase 4 lands each screen.
    private void registerScreens() {
        // e.g. screens.register(MenuType.MAIN_MENU, MainMenuScreen::new);
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

        // Note: Profile.setCurrencyObserver is deliberately left unwired here. It is a single static
        // hook, so the console CurrencyRenderer and a future HUD cannot both hold it; the GUI claims it
        // in T3.4 once there is a HUD to claim it for. Profile.notifyBalance is null-safe until then.
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
}
