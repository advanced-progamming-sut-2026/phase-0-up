package views.gdx.core;

import models.user.AppSession;
import views.Renderers;
import views.gdx.sprite.SpriteRegistry;

// Everything a Screen is allowed to reach for, handed to it in one parameter.
//
// Screens are constructed and thrown away as the player moves around, so passing five collaborators
// into every constructor gets noisy fast. This is deliberately a plain holder and not a static
// service locator: a Screen cannot reach the game from anywhere except the context it was given,
// which keeps the dependency direction obvious and makes screens constructible in isolation.
public final class GdxContext {

    private final PvZGame game;
    private final Assets assets;
    private final Toasts toasts;
    private final SpriteRegistry sprites;
    private final AppSession appSession;

    // The graphical View. A Screen builds the same Commands the terminal does and has to hand each one
    // a renderer; taking it from here rather than constructing one means a screen never decides what
    // the game's output looks like, which is the whole point of the Renderers seam.
    private final Renderers renderers;

    // Assigned once, immediately after construction. The manager needs the context to build screens and
    // the screens need the manager to navigate, so one of the two references has to be set after the
    // other exists; this is the less invasive half of that knot.
    private ScreenManager screens;

    public GdxContext(PvZGame game, Assets assets, Toasts toasts, SpriteRegistry sprites,
                      AppSession appSession, Renderers renderers) {
        this.game = game;
        this.assets = assets;
        this.toasts = toasts;
        this.sprites = sprites;
        this.appSession = appSession;
        this.renderers = renderers;
    }

    void attachScreens(ScreenManager screens) {
        this.screens = screens;
    }

    public PvZGame game() {
        return game;
    }

    public Assets assets() {
        return assets;
    }

    public Toasts toasts() {
        return toasts;
    }

    public SpriteRegistry sprites() {
        return sprites;
    }

    public AppSession appSession() {
        return appSession;
    }

    public Renderers renderers() {
        return renderers;
    }

    public ScreenManager screens() {
        return screens;
    }
}
