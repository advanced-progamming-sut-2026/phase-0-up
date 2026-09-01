package views.gdx.ui;

import java.util.regex.Pattern;

// What is worth interrupting the player for, and what the board already tells them.
//
// ## Why this exists
//
// The model narrates EVERYTHING, because the terminal build has nothing but words: every sun produced,
// every sun landing, every zombie walking on, every zombie dying, every plant destroyed. On a screen
// that is a running commentary on things the player is watching happen -- five toasts deep during a
// wave, so that the one line that actually mattered ("Not enough sun") scrolled away behind a notice
// that a sunflower made a sun.
//
// The fix is NOT to stop the model narrating. Those sentences are the terminal build's entire output
// and several view effects are driven off them (explosions, weather, pickups, NPC dialogue all listen
// to this same stream). So the stream stays complete and this decides what reaches the SCREEN.
//
// ## The rule
//
// A sentence earns a toast when the player could not otherwise know it, or must act on it:
//
//   * every refusal, without exception -- a refusal is the model saying no to something the player just
//     tried, and it is invisible on the board by definition;
//   * a wave, a necromancy, a low tide, a freezing wind -- the board is about to change in a way that
//     is only fair to warn about;
//   * a reward with no animation of its own.
//
// ## The audit
//
// Every `reportEvent` and every success `Result` that can reach the in-game path was read once and
// classified -- 37 narration sentences and about 45 command results -- rather than patterns being
// added as spam was noticed. The suppression list below is grouped in the order that sweep found them.
//
// Sun and coins are the clearest case of all. Sun is collected by sweeping the cursor over it, so
// "Collected 25 sun." fired several times a second during a harvest and buried everything else; the
// counter at the top of the screen is already the feedback, and it is exact. Coins arrive the same way
// and already fly to their counter (PickupFlights).
//
// Everything else is suppressed, because it is drawn. A sun appearing is a sun on the lawn; a zombie
// dying is a corpse; a dropped coin already flies to the counter that absorbed it (PickupFlights), and
// saying it twice reads as a bug rather than as emphasis.
public final class ToastPolicy {

    private ToastPolicy() { }

    public enum Kind {
        /** Suppressed: the board already shows this. */
        NONE,
        /** A refusal or a failure. Top-centre, red. */
        ERROR,
        /** The board is about to change. Centre-screen, red, and larger. */
        ALERT,
        /** Something was gained that nothing else announces. Top-right, gold. */
        REWARD
    }

    // Everything the board already shows, from a sweep of every reportEvent and every success Result
    // the in-game path can raise -- 37 narration sentences and about 45 command results.
    //
    // Grouped the way the audit found them, so the next person can check a new sentence against the
    // right paragraph rather than against one wall of alternation.
    private static final Pattern DRAWN_ON_THE_BOARD = Pattern.compile(
            ".*("
                    // Sun and collectibles: the counters are the feedback, and they are exact.
                    + "produced a sun|is dropping at position|Sun reached the ground"
                    + "|Collected \\d+ sun|No sun to collect|A sun-maker drops"
                    + "|dropeed a|drops \\d+ of the sun it stole"
                    // Planting and digging: the plant appears or vanishes under the cursor.
                    + "|is in the ground at|Plant placed successfully|Plant removed successfully"
                    + "|Platform placed|Platform removed|Protective cover placed"
                    + "|Protective cover removed|Pea Pod stacked|You tore up the"
                    // Zombies arriving, dying, and every ability that has art of its own.
                    + "|spawned at wave|claws its way up|is dead at|is destroyed\\."
                    + "|wanders off the far end|smashes|hurls its Imp|hurls ice at"
                    + "|fires a laser beam|flings an octopus|rams its arcade machine|tackles"
                    + "|takes aim at|winds up an octopus|raises its staff|raises his sceptre"
                    // The sun thieves powering up and down. The zombie visibly lights up and the
                    // counter visibly drains; a line for each end of every heist is running commentary.
                    + "|powers up|powers down|levels its skull"
                    // The wizard's hex, both ways round: the plant visibly BECOMES a sheep and
                    // visibly comes back when its caster dies. See PlantSheep.
                    + "|is hexed into a sheep|shakes off the hex"
                    + "|knights a peasant|raises a grave|starts chanting|The Troglobite"
                    + "|The Barrel Roller falls"
                    + "|The barrel bursts open|The rolling barrel crushes|The pianist strikes up"
                    + "|The piano smashes|The Fisherman Zombie|The Snorkel Zombie"
                    + "|The Jester Zombie|A frozen block shatters|arcade machine falls apart"
                    + "|dynamite explodes|dynamite fizzles"
                    + "|torch flares back to life|torch is snuffed out"
                    + "|ignores the chill|shrugs off the freeze"
                    // Shots, blasts and terrain damage.
                    + "|detonates at|Grapeshot bursts|A grave crumbles at|The grave at"
                    // NOT the Imp Dragon's fire immunity. That one belongs on screen: a fire-proof
                    // zombie is a RULE the board cannot show -- the pea bursts on it exactly as it
                    // would on any other zombie and the health simply does not move, which reads as a
                    // broken plant. The model now says it once per dragon (see StateComponent), so it
                    // is a single line rather than one a second.
                    + "|reflect|deflects|swats away"
                    // Cheats confirm themselves through the counter they just moved.
                    + "|Cooldowns gone|One fresh plant food|KA-BOOM|Kaboom! All|Sunny day!"
                    // Mini-game handling that is visible in the hand or on the lane.
                    + "|Got it! A|Thunk! The"
                    // The tornado's per-zombie line; WeatherEffects paints the burst on that tile.
                    + "|The tornado drops"
                    + ").*",
            Pattern.CASE_INSENSITIVE);

    // The board is about to change in a way it is only fair to warn about. Checked BEFORE the
    // suppression list, so a sentence that appears in both is still shown.
    private static final Pattern ALERT = Pattern.compile(
            ".*(Wave \\d+ started|necromancy|low tide|The tide rises|The tide recedes"
                    + "|The tide can flood|A freezing wind sweeps|A tornado tears across"
                    // A king arriving is exactly what this list is for: it parks on the far column,
                    // out of most plants' reach, and every few seconds the lane in front of it gets
                    // harder. Knowing it is there is the difference between answering it and wondering
                    // why the peasants keep turning up in armour.
                    + "|takes his throne"
                    + "|The lawn mower in the row).*",
            Pattern.CASE_INSENSITIVE);

    // A gain the player would otherwise have to go looking for. Deliberately short: the loot drops are
    // NOT here, because PickupFlights already flies each one to its counter and says what it was.
    private static final Pattern REWARD = Pattern.compile(
            ".*(Quest complete|seed packets for|unlocked successfully|Pot at \\().*",
            Pattern.CASE_INSENSITIVE);

    // `success` is the Result's own flag: false always means the model refused something.
    public static Kind classify(String message, boolean success) {
        if (message == null || message.isBlank()) {
            return Kind.NONE;
        }
        if (!success) {
            return Kind.ERROR;
        }
        // Anything an NPC says out loud is never also a toast.
        //
        // This is the "banner appears twice" the Phase 10 hand-off recorded as known polish: every
        // special mode announces its rules in onStart, Penny delivers those in a box that HOLDS, and
        // the same three or four lines were simultaneously stacking up in the corner and fading. Same
        // for "The final wave has come.", which Zomboss says. Asked of NpcLines rather than re-listed
        // here, so a banner given to a new speaker tomorrow cannot come back as a duplicate.
        if (NpcLines.speakerFor(message) != null) {
            return Kind.NONE;
        }
        if (ALERT.matcher(message).matches()) {
            return Kind.ALERT;
        }
        if (DRAWN_ON_THE_BOARD.matcher(message).matches()) {
            return Kind.NONE;
        }
        if (REWARD.matcher(message).matches()) {
            return Kind.REWARD;
        }
        // Anything unrecognised is shown rather than swallowed. A new sentence nobody has classified is
        // a message the player still gets to read; the failure mode of the other default -- silence --
        // is a feature that looks broken with nothing in the log to say why.
        return Kind.REWARD;
    }
}
