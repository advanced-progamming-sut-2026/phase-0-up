package views.gdx.core;

// One place for every developer-facing toggle, so they are discoverable instead of scattered through
// the code as ad-hoc System.getProperty calls.
//
// All are read once at startup and default to off, so none of them costs anything in a normal run.
// Pass them to either entry point, e.g.:
//
//   gradlew runGui -Dpvz.uiDebug=1 -Dpvz.glProfile=1
//
// (build.gradle forwards any -Dpvz.* flag from the Gradle command line into the forked GUI JVM.)
public final class DebugFlags {

    // Draws Scene2D layout bounds: every Table cell, every Actor rectangle. The fastest way to find
    // out why a widget is not where you expected -- the drawn pixels tell you nothing about the cell.
    public static final boolean UI_DEBUG = flag("pvz.uiDebug");

    // Logs draw calls, texture bindings and shader switches once a second via LibGDX's GLProfiler.
    // Worth watching when adding renderers: every setTransformMatrix or setColor change can flush the
    // SpriteBatch, and a flush is a draw call.
    public static final boolean GL_PROFILE = flag("pvz.glProfile");

    // Per-second census of what is actually on the board (plants / zombies / projectiles / suns).
    // Answers "is it not being drawn, or is it not there?" without squinting at a screenshot.
    public static final boolean BOARD_COUNTS = flag("pvz.debugCounts");

    // Sets the board up so every hard-to-trigger effect is on screen at once, for checking by eye.
    // See views.gdx.screens.Showcase.
    public static final boolean SHOWCASE = flag("pvz.showcase");

    // Starts the level paused. Lets a screenshot capture the pause overlay, and is handy for looking
    // at a board's opening state without it walking away from you.
    public static final boolean START_PAUSED = flag("pvz.pause");

    // Skips the level's objective card, which otherwise opens paused over the board and waits for a
    // click. Every unattended run needs this -- a screenshot harness and the showcase both exist to
    // look at a board that is actually running.
    public static final boolean SKIP_INTRO = flag("pvz.skipIntro");

    // Parks the pointer on a main-menu button and reports whether the hover animation moved it. A
    // screenshot run has no mouse, so this is the only way to check the effect unattended.
    public static final boolean HOVER_CHECK = flag("pvz.hoverCheck");

    // Round-trips every lawn tile through project/unproject and reports any that come back as a
    // different tile, then plants one seed by simulated click. Verifies the screen-to-tile half of the
    // input path, which no unit test can reach because it needs a live viewport.
    public static final boolean INPUT_CHECK = flag("pvz.inputCheck");

    // Pins every plant to one damage stage (1..3), so a Wall-nut's cracked shells can be looked at
    // without waiting forty seconds for zombies to chew through 4000 HP. -1 leaves health in charge.
    public static final int FORCE_DAMAGE_STAGE = number("pvz.forceDamage");

    // Clicks a world card on the Adventure screen a second after it opens, and reports what every card
    // did. Same reason as HOVER_CHECK: a screenshot run has no mouse, and "picking a world animates"
    // cannot be seen in a still frame or asserted in a unit test. Pair it with -Dpvz.smokeFrames to
    // land the capture mid-transition or after it settles. -1 is off.
    public static final int WORLD_CHECK = number("pvz.worldCheck");

    // Presses Escape on whichever menu screen is open and reports where the session ended up.
    //
    // Escape and the Back button run the same goBack(), so this checks the button's route without a
    // mouse. Permanent rather than a one-off because Back has now been reported broken three times, and
    // every failure looked identical from outside: the command IS posted, the model refuses it, the
    // refusal arrives as a toast, and the button appears to do nothing.
    //
    //   gradlew runGui -Dpvz.menu=plants -Dpvz.backCheck=1 -Dpvz.smokeFrames=45
    public static final boolean BACK_CHECK = flag("pvz.backCheck");

    private DebugFlags() { }

    private static int number(String key) {
        try {
            return Integer.parseInt(System.getProperty(key, "-1").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean flag(String key) {
        String value = System.getProperty(key);
        return value != null && !value.isEmpty() && !"0".equals(value) && !"false".equalsIgnoreCase(value);
    }
}
