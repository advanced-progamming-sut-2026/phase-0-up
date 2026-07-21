package views.renderers.MenuRenderer;

public class MainMenuRenderer {
    // ANSI red so the unread-news marker actually shows up as a red badge in a terminal; the "NEW"
    // text keeps it meaningful where colour is stripped (logs, redirected output, tests).
    private static final String RED_BADGE = "[31m[* NEW][0m";

    public void logOutRender(boolean success){
        if(success){
            System.out.println("Signed out. See you on the lawn! You're back at the Sign-Up menu.");
        }
        else {
            System.out.println("You're not signed in, so there's nothing to sign out of.");
        }
    }

    // The main menu button list. The News button carries a red notification badge whenever the player
    // has unread news; without unread news it renders plain.
    public void showMainMenu(boolean hasUnreadNews) {
        System.out.println("===== Main Menu =====");
        System.out.println("- Play      (menu enter play)");
        System.out.println("- Profile   (menu enter profile)");
        System.out.println("- Settings  (menu enter settings)");
        System.out.println("- News " + (hasUnreadNews ? RED_BADGE : "") + "   (menu enter news)");
        System.out.println("- Logout    (menu logout)");
    }
}
