package views.gdx.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.Disposable;
import controllers.engine.MenuType;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// Decides which Screen is on screen -- by watching the model, not by being told.
//
// The terminal game already has a complete menu state machine: MenuType on AppSession, with
// EnterMenuCommand/ExitMenuCommand policing the legal transitions and several Commands (ChooseLevel,
// StartLevel, the mini-game launchers) setting it directly. Re-implementing any of that GUI-side would
// mean two sources of truth that drift.
//
// So this does not own navigation. Every frame it compares AppSession.getCurrentMenu() against what is
// displayed and catches up. A button handler runs the same Command the CLI runs; the Command moves the
// session; the screen follows. Menu rules stay in exactly one place.
public final class ScreenManager implements Disposable {

    private final GdxContext context;
    private final Map<MenuType, Function<GdxContext, Screen>> factories = new EnumMap<>(MenuType.class);

    // Screens are rebuilt on entry rather than cached, so a menu always reflects current profile data
    // (coins spent, a plant just unlocked) with no invalidation logic. They are cheap; the Stage is the
    // only real cost. This is the one we must dispose ourselves -- Game.setScreen only calls hide().
    private Screen current;
    private MenuType shown;

    // MenuTypes already reported as having no screen, so the log does not repeat 60 times a second
    // while the GUI is still being built out.
    private final Set<MenuType> warned = EnumSet.noneOf(MenuType.class);

    public ScreenManager(GdxContext context) {
        this.context = context;
        context.attachScreens(this);
    }

    public void register(MenuType type, Function<GdxContext, Screen> factory) {
        factories.put(type, factory);
    }

    // Asks the model to move, then lets sync() do the actual swap. Going through AppSession rather than
    // straight to setScreen keeps the session authoritative even for navigation the GUI initiates.
    public void goTo(MenuType type) {
        context.appSession().setCurrentMenu(type);
        sync();
    }

    // Called once per frame, before the active screen renders.
    public void sync() {
        MenuType target = context.appSession().getCurrentMenu();
        if (target == null || target == shown) {
            return;
        }
        Function<GdxContext, Screen> factory = factories.get(target);
        if (factory == null) {
            // Expected while the GUI is incomplete: stay on the current screen rather than blanking it.
            if (warned.add(target)) {
                Gdx.app.log("ScreenManager", "no screen registered for " + target
                        + " yet -- staying on " + shown);
            }
            return;
        }
        swapTo(factory.apply(context), target);
    }

    // Shows a screen that is not part of the MenuType map (a splash, or a Phase 0 harness screen).
    public void showDetached(Screen screen) {
        swapTo(screen, null);
    }

    private void swapTo(Screen next, MenuType type) {
        Screen previous = current;

        current = next;
        shown = type;
        context.game().setScreen(next);

        // After setScreen, so the outgoing screen has already had hide() called on it. Disposing before
        // would pull the rug out from under a screen that is still notionally active.
        if (previous != null) {
            previous.dispose();
        }
    }

    public MenuType currentMenu() {
        return shown;
    }

    @Override
    public void dispose() {
        if (current != null) {
            current.dispose();
            current = null;
        }
    }
}
