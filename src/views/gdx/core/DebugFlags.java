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

    // Starts the level paused. Lets a screenshot capture the pause overlay, and is handy for looking
    // at a board's opening state without it walking away from you.
    public static final boolean START_PAUSED = flag("pvz.pause");

    // Round-trips every lawn tile through project/unproject and reports any that come back as a
    // different tile, then plants one seed by simulated click. Verifies the screen-to-tile half of the
    // input path, which no unit test can reach because it needs a live viewport.
    public static final boolean INPUT_CHECK = flag("pvz.inputCheck");

    // Pins every plant to one damage stage (1..3), so a Wall-nut's cracked shells can be looked at
    // without waiting forty seconds for zombies to chew through 4000 HP. -1 leaves health in charge.
    public static final int FORCE_DAMAGE_STAGE = number("pvz.forceDamage");

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
