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

    // True from the moment an outgoing screen starts its exit until the swap actually happens.
    //
    // sync() runs every frame, so without this the same menu change would restart the exit animation on
    // every one of the twelve frames it takes to play -- the screen would fade a little, snap back and
    // fade again, and never leave.
    private boolean leaving;

    // Called once per frame, before the active screen renders.
    public void sync() {
        if (leaving) {
            return;   // an exit animation is in flight; the swap is already booked
        }
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
        transitionTo(factory, target);
    }

    // Lets the outgoing screen animate itself out first, if it can.
    //
    // The new screen is built AFTER the animation rather than before, so a menu still shows what the
    // model said at the moment it actually appears -- building it up front would capture the profile as
    // it was a fifth of a second earlier, which for a screen that reads a wallet or a badge is a stale
    // number on arrival.
    //
    // A screen that is not Transitioning -- GameScreen, the sprite harness -- swaps instantly, exactly
    // as before.
    private void transitionTo(Function<GdxContext, Screen> factory, MenuType target) {
        if (!(current instanceof Transitioning outgoing)) {
            swapTo(factory.apply(context), target);
            return;
        }
        leaving = true;
        outgoing.playExit(() -> {
            // Deferred to the top of the next frame, NOT run here.
            //
            // This callback fires from inside the outgoing Stage's act(), and swapTo disposes that very
            // Stage -- so running it directly would dispose a Stage that Scene2D is still iterating,
            // and the rest of act() would walk freed actors and a disposed batch. postRunnable puts the
            // swap outside the traversal, which is the only safe place for it.
            Gdx.app.postRunnable(() -> {
                leaving = false;
                swapTo(factory.apply(context), target);
            });
        });
    }

    // Rebuilds the screen for the menu the session is already in.
    //
    // sync() deliberately does nothing when the target menu has not changed, which is right for
    // navigation and wrong for restarting a level: the player stays in IN_GAME while everything behind
    // the screen -- a brand new GameSession, a brand new GameEngine -- is replaced. This is the one
    // case where the screen has to be thrown away without the menu moving.
    public void reopen() {
        MenuType target = context.appSession().getCurrentMenu();
        Function<GdxContext, Screen> factory = target == null ? null : factories.get(target);
        if (factory == null) {
            Gdx.app.error("ScreenManager", "cannot reopen " + target + " -- no screen registered");
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
