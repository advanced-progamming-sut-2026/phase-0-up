package views.gdx.bridge;

import controllers.engine.InputRouter;

import java.util.regex.Pattern;

// What a menu button does.
//
// The in-game counterpart of this is CommandBridge: a click becomes the same string the prompt takes,
// and the model's own routing decides what it means. This does the same for menus, through
// InputRouter.submit -- so the rule about which command is legal in which menu is written once, in the
// router, and the two builds cannot disagree about it.
//
// The alternative -- each screen constructing Commands itself -- would have every screen re-deciding
// what "you are not signed in" means and drifting from the terminal within a week.
public final class MenuCommands {

    // Three commands must never come through here, because the router answers them by blocking.
    //
    //   register / forget password -> read stdin mid-execute for the security question
    //   start                      -> runs GameEngine.startLoop(), the terminal's blocking game loop
    //
    // On a render thread any of the three freezes the window with no error and no way out. Each has a
    // non-blocking path a screen is meant to use instead (the non-interactive Command constructors from
    // T3.5; StartLevelCommand on its own, letting GameScreen drive the loop). Catching them here turns
    // "the window hangs" into a log line naming the caller's mistake.
    private static final Pattern BLOCKING = Pattern.compile(
            "^\\s*(register\\s.*|forget\\s+password\\s.*|start\\s+game\\s*)$", Pattern.CASE_INSENSITIVE);

    private final InputRouter router;

    public MenuCommands(InputRouter router) {
        this.router = router;
    }

    // Returns whether the command was posted. False means it was refused before reaching the router --
    // never that the router rejected it, which the player learns about through a toast as usual.
    public boolean submit(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        if (BLOCKING.matcher(command).matches()) {
            com.badlogic.gdx.Gdx.app.error("MenuCommands",
                    "refused to submit a blocking command from the GUI: \"" + command + "\" -- use the "
                            + "non-interactive Command directly instead");
            return false;
        }
        router.submit(command);
        return true;
    }

    // Menu navigation, which every screen needs and none should spell out by hand.
    public void enter(String menuName) {
        submit("menu enter " + menuName);
    }

    public void back() {
        // "menu exit", not "exit menu". AllMenuRegex.EXIT_MENU is ^menu\s+exit$ -- the verb comes
        // second in this grammar, the same way "menu enter play" does. Reversed, it matched nothing
        // (and not EXIT_APPLICATION either, which allows only "exit" or "exit application"), so every
        // Back button fell through the router to invalidCommand and did nothing but raise a toast.
        submit("menu exit");
    }
}
