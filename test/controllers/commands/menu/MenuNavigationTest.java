package controllers.commands.menu;

import controllers.engine.MenuType;
import models.user.AppSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.Result;
import views.renderers.MenuRenderer.AllMenuRenderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

// Pins which command actually takes a player BACK.
//
// EnterMenuCommand and ExitMenuCommand look interchangeable from a screen -- both move the session's
// current menu -- and they are not. Entering walks an explicit edge list that only covers the forward
// routes; going back is ExitMenuCommand's job and nothing else's. Getting that wrong is silent from
// the GUI's side: the command is dispatched, refused, and the refusal arrives as a toast, so the
// button "does nothing" and no exception is ever thrown.
//
// It has now cost three Back buttons -- the login screen's, the seed-selection screen's, and (for a
// different reason) every screen's, when MenuCommands.back posted "exit menu" instead of "menu exit".
// These tests hold the two halves of each route against each other so the next one fails here first.
class MenuNavigationTest {

    // Records instead of printing. What the commands SAY is not what is being tested -- where they
    // leave the session is.
    private static final class SilentRenderer implements AllMenuRenderer {
        @Override
        public void showCurrentMenu(MenuType menu) {
        }

        @Override
        public void enterMenu(Result result) {
        }

        @Override
        public void menuExit(String destination) {
        }

        @Override
        public void applicationExit() {
        }

        @Override
        public void invalidCommand() {
        }
    }

    private final AllMenuRenderer renderer = new SilentRenderer();

    private AppSession sessionAt(MenuType menu) {
        AppSession session = new AppSession();
        session.setCurrentMenu(menu);
        return session;
    }

    @Test
    @DisplayName("leaving seed selection lands on the play menu")
    void plantsMenuExitsToPlay() {
        AppSession session = sessionAt(MenuType.PLANTS_MENU);
        new ExitMenuCommand(session, renderer).execute();
        assertEquals(MenuType.PLAY_MENU, session.getCurrentMenu());
    }

    @Test
    @DisplayName("entering the play menu from seed selection is refused, so Back may not use it")
    void plantsMenuCannotEnterPlay() {
        AppSession session = sessionAt(MenuType.PLANTS_MENU);
        // Without a user, isReachable refuses before the edge list is even consulted -- so one is
        // needed here, or this would be testing the sign-in gate instead of the edge.
        session.setCurrentUser(new models.user.User("tester", "Tester", "t@example.com",
                models.user.Gender.MALE, "hash", 0, "answer"));
        new EnterMenuCommand(session, MenuType.PLAY_MENU.getMenuName(), renderer).execute();
        assertEquals(MenuType.PLANTS_MENU, session.getCurrentMenu(),
                "EnterMenuCommand has no edge out of the plants menu -- Back must post \"menu exit\"");
    }

    @Test
    @DisplayName("leaving the login menu lands on sign-up")
    void loginMenuExitsToSignUp() {
        AppSession session = sessionAt(MenuType.LOGIN_MENU);
        new ExitMenuCommand(session, renderer).execute();
        assertEquals(MenuType.SIGNUP_MENU, session.getCurrentMenu());
    }

    @Test
    @DisplayName("entering sign-up from the login menu is refused, so Back may not use it")
    void loginMenuCannotEnterSignUp() {
        AppSession session = sessionAt(MenuType.LOGIN_MENU);
        new EnterMenuCommand(session, MenuType.SIGNUP_MENU.getMenuName(), renderer).execute();
        assertEquals(MenuType.LOGIN_MENU, session.getCurrentMenu(),
                "EnterMenuCommand allows login -> main only -- Back must post \"menu exit\"");
    }

    @Test
    @DisplayName("every menu a screen can exit from has somewhere to go")
    void everyExitLeadsSomewhere() {
        // The one rule a Back button depends on. ExitMenuCommand is a switch with no default, so a
        // MenuType added later and forgotten leaves the session exactly where it was -- a Back button
        // that does nothing, on a screen nobody thought to re-test.
        for (MenuType from : MenuType.values()) {
            if (from == MenuType.SIGNUP_MENU || from == MenuType.MAIN_MENU || from == MenuType.IN_GAME) {
                continue;   // deliberate dead ends: the sign-up wall, the top menu, and the lawn
            }
            AppSession session = sessionAt(from);
            new ExitMenuCommand(session, renderer).execute();
            assertNotEquals(from, session.getCurrentMenu(),
                    "ExitMenuCommand has no route out of " + from + " -- Back there does nothing");
        }
    }
}
