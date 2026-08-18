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

    // Opens a tabbed screen on a named tab instead of its default. The almanac's zombie half is two
    // clicks in and therefore invisible to an unattended screenshot run, which is the same problem
    // -Dpvz.menu solves one level up. Empty means "whatever the screen would have picked".
    public static final String START_TAB = text("pvz.tab");

    // Presses the first button with this label, a second after the screen opens, and says whether it
    // found one. The general form of BACK_CHECK: anything a screen only shows AFTER a click -- a
    // confirmation dialog, a filtered list, a detail page -- is invisible to a screenshot run
    // otherwise. Matched case-insensitively against the button's own text.
    //
    //   gradlew runGui -Dpvz.menu=shop -Dpvz.click=Buy -Dpvz.smokeFrames=45
    public static final String CLICK_LABEL = text("pvz.click");

    // Logs the foot-planting curve for the first zombie drawn each frame: how far through its walk cycle
    // it is, and how far off its straight-line position it is being drawn.
    //
    // Skating is motion, so no screenshot can show it and no unit test can reach a live viewport. What
    // CAN be checked is the curve: the lead must be zero at both ends of the cycle (or the loop jumps),
    // must never accumulate (or the drawing drifts off the model), and must be negative through the
    // stance (the body waiting on a planted foot while the straight line runs ahead).
    //
    //   gradlew runGui -Dpvz.screen=game -Dpvz.skipIntro=1 -Dpvz.footCheck=1 -Dpvz.smokeFrames=400
    public static final boolean FOOT_CHECK = flag("pvz.footCheck");

    // Opens the almanac on a named entity instead of whichever one it would have picked.
    //
    // The grid scrolls and shows about two and a half rows, so anything past the twentieth zombie cannot
    // be reached by a screenshot run at all -- Newspaper Zombie is the twenty-second, which is why its
    // resting-clip bug survived every capture taken of this screen. Matched against the plant's display
    // name or the zombie's registry alias, ignoring case.
    //
    //   gradlew runGui -Dpvz.menu=collection -Dpvz.tab=zombies -Dpvz.entity=ZombieNewspaper
    public static final String ENTITY = text("pvz.entity");

    // Drives the greenhouse the way a player would, because none of it can be reached otherwise.
    //
    //   1  fills the first two pots and finishes one, so all four states -- locked, empty, growing and
    //      ripe -- are on screen in the same frame
    //   2  then harvests the ripe one, which is the only way to see the reward modal
    //   3  then clicks the growing one, which is the only way to see the gem speed-up dialog
    //
    // -Dpvz.click cannot reach a pot: it is a clickable Table, not a TextButton, and the states it has
    // to show otherwise take two and eight HOURS to arrive. Every hop is a real command, so this checks
    // the plant, grow and collect paths as well as the look. It DOES spend a few gems and write to the
    // save, which is why it is opt-in. -1 is off.
    //
    //   gradlew runGui -Dpvz.menu=greenhouse -Dpvz.potCheck=2 -Dpvz.smokeFrames=60
    public static final int POT_CHECK = number("pvz.potCheck");

    private DebugFlags() { }

    private static String text(String key) {
        String value = System.getProperty(key);
        return value == null ? "" : value.trim();
    }

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
