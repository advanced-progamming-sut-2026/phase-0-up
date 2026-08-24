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

    // Puts N plants on the seed bar, so a capture shows filled slots, empty slots and the grid with
    // those plants taken out of it in the same frame.
    //
    // -Dpvz.click cannot reach a seed card: cards are clickable Tables, not TextButtons, and an
    // unattended run has no pointer to hover one with either -- which also means the detail strip is
    // stuck on whatever plant the screen focused for itself. Every hop is a real "add plant" command,
    // so this exercises the add path as well as the look. -1 is off.
    //
    //   gradlew runGui -Dpvz.menu=plants -Dpvz.seedCheck=3 -Dpvz.smokeFrames=60
    public static final int SEED_CHECK = number("pvz.seedCheck");

    // Smashes N vases on a Vasebreaker board by simulated click, then picks up whatever they dropped.
    //
    // Every other state of that board -- a smashed vase, a packet lying on the grid, a hand with
    // anything in it -- is reachable only by clicking, and a lawn tile is not a TextButton so
    // -Dpvz.click cannot reach one. Going through LawnInputProcessor rather than posting the command
    // directly is the point: it exercises the unproject-to-tile half of the path as well as the look.
    //
    //   gradlew runGui -Dpvz.screen=game -Dpvz.devMinigame=vasebreaker -Dpvz.vaseCheck=6
    public static final int VASE_CHECK = number("pvz.vaseCheck");

    // Rolls N nuts off Wall-nut Bowling's conveyor, one per lane, by simulated click.
    //
    // A nut only exists while it is travelling, so there is no still frame of that board that shows one
    // without somebody having bowled it first -- and the belt cards are Scene2D actors on a tile-less
    // HUD, so neither -Dpvz.click nor a tile click reaches the pair of gestures this needs. Arms the
    // card through ToolState and clicks the lawn, exactly as a player would.
    //
    //   gradlew runGui -Dpvz.screen=game -Dpvz.devMinigame=bowling -Dpvz.bowlCheck=3 -Dpvz.smokeFrames=150
    public static final int BOWL_CHECK = number("pvz.bowlCheck");

    // Summons N zombies off I, Zombie's roster, one per lane, by simulated click.
    //
    // Same problem as the belt: arming a roster card is a Scene2D click and placing the zombie is a lawn
    // click, and no single existing flag does both. Buys the cheapest zombies first so a starting bank
    // of 300 sun actually stretches to a few.
    //
    //   gradlew runGui -Dpvz.screen=game -Dpvz.devMinigame=izombie -Dpvz.summonCheck=3
    public static final int SUMMON_CHECK = number("pvz.summonCheck");

    // Runs N game ticks in one frame, shortly after the board opens, through the game's own
    // `advance time` command.
    //
    // The world effects are the reason: a freezing wind blows when a WAVE starts and Egypt's tornado
    // only on the final one, so both are twenty-five to several hundred seconds of real play away, and
    // each lasts about two seconds once it does arrive. Fast-forwarding to the wave and then capturing
    // a few frames later is the only way a still frame catches either.
    //
    //   gradlew runGui -Dpvz.screen=game -Dpvz.devChapter=2 -Dpvz.fastForward=300 -Dpvz.smokeFrames=70
    public static final int FAST_FORWARD = number("pvz.fastForward");

    // With -Dpvz.fastForward: N rounds of "clear the board, let the next wave come".
    //
    // Waves after the first are gated by HEALTH -- the next launches once 75% of the current one's HP
    // is gone -- so no amount of advancing time alone reaches a late wave. Egypt's tornado fires on the
    // FINAL wave and nowhere else, which made it the one world effect no capture could get to.
    //
    //   gradlew runGui -Dpvz.screen=game -Dpvz.fastForward=260 -Dpvz.rushWaves=6 -Dpvz.smokeFrames=60
    public static final int RUSH_WAVES = number("pvz.rushWaves");

    // Raises the end-of-level panel without playing to the end: "win" or "lose".
    //
    // Losing on purpose is surprisingly hard to stage -- the lawnmowers save every lane once, waves are
    // health-gated so time alone does not advance them, and a board with no plants still takes several
    // minutes to fall. The panel is pure VIEW, so a view-only shortcut to it is the honest instrument:
    // it shows exactly what a real loss shows, because it is the same call GameScreen makes.
    //
    //   gradlew runGui -Dpvz.screen=game -Dpvz.showOutcome=lose -Dpvz.smokeFrames=50
    public static final String SHOW_OUTCOME = text("pvz.showOutcome");

    // Logs every camera shake as it is raised, and then the curve it follows frame by frame.
    //
    // A shake is motion and lasts about half a second, so no screenshot can show it and no still frame
    // can tell a correct one from a camera that has simply come off its rails -- the same problem the
    // foot planting and the weather have. What CAN be checked is the curve: it has to decay to exactly
    // zero, never exceed the margin the zoom-in creates, and start from the events it claims to.
    //
    // Pair it with the showcase, whose Jalapeno detonates within the first second:
    //
    //   gradlew runGui -Dpvz.showcase=1 -Dpvz.skipIntro=1 -Dpvz.shakeCheck=1 -Dpvz.smokeFrames=120
    public static final boolean SHAKE_CHECK = flag("pvz.shakeCheck");

    // Reports every audio file the game resolves, and every name it wanted and could not find.
    //
    // A missing file is already logged once on its own, but the SUCCESSFUL case is the one that cannot
    // otherwise be checked: a screenshot run has no speakers, so "the log stayed quiet" is equally
    // consistent with the audio working and with nothing ever having asked for it. This says which file
    // won each fallback chain, which is the only way to confirm that e.g. Frostbite is playing
    // lawn_frostbite rather than falling through to a generic lawn track.
    //
    //   gradlew runGui -Dpvz.screen=game -Dpvz.devChapter=2 -Dpvz.audioCheck=1 -Dpvz.smokeFrames=40
    public static final boolean AUDIO_CHECK = flag("pvz.audioCheck");

    // Logs, for every zombie drawn, the lane the MODEL puts it in against the lane it is actually
    // DRAWN at, plus the foot line each of those implies.
    //
    // "It is rendered a row off" is a report a screenshot cannot settle: a tall sprite legitimately
    // covers the rows above its feet, so the eye cannot tell a misplaced zombie from a big one. These
    // are the only two numbers that can disagree -- the model's lane, and the interpolated lane the
    // renderer stands it on -- and if they agree the drawing is correct by construction.
    //
    //   gradlew runGui -Dpvz.screen=game -Dpvz.showcase=1 -Dpvz.skipIntro=1 -Dpvz.laneCheck=1
    public static final boolean LANE_CHECK = flag("pvz.laneCheck");

    // Puts named zombies on the lawn, one per lane, a moment after the board opens.
    //
    // Fills the last gap the other flags leave. Everything else here can reach a board, a screen or a
    // widget, but there was no way at all to look at ONE named zombie standing on real ground: the
    // sprite viewer draws an animation with no lane, no scale and no armour, the almanac draws
    // undiscovered zombies as silhouettes, and which zombie a wave buys is the wave's choice, not
    // yours. Adding a never-before-drawn entity is a view change, and this is the instrument for it --
    // the Zombotany plant-heads were built against it.
    //
    // Comma-separated registry aliases, laid out from lane 0 down. Goes through the game's own
    // `cheat spawn-zombie` command, so a name the registry does not know is refused with a message
    // rather than silently drawing nothing.
    //
    //   gradlew runGui -Dpvz.screen=game -Dpvz.skipIntro=1 -Dpvz.view=760 \
    //       -Dpvz.spawn=ZombieBotanyPeashooter,ZombieBotanyWallnut -Dpvz.smokeFrames=60
    public static final String SPAWN = text("pvz.spawn");

    // Which column -Dpvz.spawn drops them into. Middle of the lawn by default, which is where a zoomed
    // camera is looking; the spawn edge at x=9.5 is off screen at anything but the full view.
    public static final int SPAWN_COLUMN = number("pvz.spawnColumn");

    // Runs in-game commands, semicolon-separated, a moment after the board opens.
    //
    // The general form of half the flags above, and the one to reach for before writing another. Every
    // in-game verb is a typeable command and `GameEngine.routeAndExecute` is public, but in the GUI the
    // prompt does not exist: the only way to a cheat is the cheat panel, whose buttons are clickable
    // Tables rather than TextButtons, so -Dpvz.click cannot press one either. Anything expressible as a
    // command therefore needed a bespoke flag until this.
    //
    // What it does NOT replace are the flags that drive GESTURES -- arming a seed and then clicking a
    // tile is two interactions with no command spelling, which is why -Dpvz.vaseCheck, -Dpvz.bowlCheck,
    // -Dpvz.summonCheck and -Dpvz.spawn still exist.
    //
    // Set up a board and then blow it up, which is how the scoring game's simultaneous-kill award was
    // checked:
    //
    //   gradlew runGui -Dpvz.screen=game -Dpvz.devMinigame=scoring -Dpvz.skipIntro=1 \
    //       "-Dpvz.spawn=ZombieDefault,ZombieDefault,ZombieDefault" -Dpvz.spawnColumn=4 \
    //       "-Dpvz.run=plant plant -t Cherry Bomb -l (4, 1)" -Dpvz.smokeFrames=70
    public static final String RUN = text("pvz.run");

    // Makes N matches on a Beghouled board by trying neighbours, through the real click path.
    //
    // Everything on that board worth looking at happens only AFTER a successful swap -- a cascade
    // settling, craters, enough sun to afford an upgrade, a match counter past zero -- and a swap is
    // two clicks on lawn tiles, which -Dpvz.click cannot reach. -Dpvz.run could post the command
    // string, but that skips the whole half of the gesture that is new: unproject, pick the tile,
    // decide it is a neighbour, build the string. See MinigameHarness.runSwapCheck. -1 is off.
    //
    //   gradlew runGui -Dpvz.screen=game -Dpvz.devMinigame=beghouled -Dpvz.skipIntro=1 \
    //       -Dpvz.swapCheck=3 -Dpvz.smokeFrames=120
    public static final int SWAP_CHECK = number("pvz.swapCheck");

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
