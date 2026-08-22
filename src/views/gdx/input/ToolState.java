package views.gdx.input;

// What the cursor is currently holding.
//
// The terminal build has no equivalent: typing "pluck plant -l (2, 1)" names the verb and the target
// in one breath, so there is nothing to remember between commands. A mouse splits that in two -- pick
// up the shovel, then choose a tile -- and this is the half that has to be held in between.
//
// Deliberately view-only. The model is never told which tool is selected; it only ever sees the
// finished command, exactly as the terminal produces it.
public final class ToolState {

    public enum Tool {
        /** Nothing held. Clicks on the lawn do nothing. */
        NONE,
        /** A seed packet is armed; clicking a tile plants it. */
        SEED,
        /** The shovel is out; clicking a tile digs up whatever is on it. */
        SHOVEL,
        /** Plant food is armed; clicking a plant feeds it. */
        PLANT_FOOD,
        /** A bowling nut is off the conveyor; clicking a tile behind the red line rolls it. */
        NUT,
        /** A zombie is off I-Zombie's roster; clicking a tile right of the red line summons it. */
        ZOMBIE
    }

    private Tool tool = Tool.NONE;

    // What is held, whatever it is. Exactly one thing can be held at a time, so one field is the honest
    // shape -- and every reader goes through an accessor that checks the TOOL first, so asking for the
    // seed while a nut is armed answers null rather than a stale name. Three parallel fields would let a
    // mismatched read return something plausible and build a command the router matches nothing for,
    // which fails silently; this cannot.
    private String held;

    public Tool tool() {
        return tool;
    }

    // The plant this seed packet will place, in the template's own display casing ("Snow Pea"), which
    // is what the plant command expects. Null unless a seed is armed.
    public String seedName() {
        return tool == Tool.SEED ? held : null;
    }

    // The nut this throw will roll, as the token `bowl -t <token>` takes ("bowling", "explode",
    // "giant"). Null unless a nut is armed.
    public String nutToken() {
        return tool == Tool.NUT ? held : null;
    }

    // The zombie this summon will raise, as the registry alias `summon -t <alias>` takes
    // ("ZombieGargantuar"). Null unless a zombie is armed.
    public String zombieAlias() {
        return tool == Tool.ZOMBIE ? held : null;
    }

    // Whatever is held, without caring which kind it is. For the cursor, which draws all three the
    // same way -- a sprite ghosted under the pointer.
    public String heldName() {
        return isHolding() ? held : null;
    }

    public boolean isHolding() {
        return tool != Tool.NONE;
    }

    // Arms a seed packet. Selecting the packet that is already armed puts it back -- clicking a card
    // twice to cancel is how the original game behaves, and it saves needing a separate cancel target.
    public void selectSeed(String name) {
        arm(Tool.SEED, name);
    }

    // Same toggle rule as a seed packet: taking the nut you are already holding puts it back.
    public void selectNut(String token) {
        arm(Tool.NUT, token);
    }

    // Same toggle rule again: picking the zombie already on the cursor puts it back on the roster.
    public void selectZombie(String alias) {
        arm(Tool.ZOMBIE, alias);
    }

    private void arm(Tool wanted, String name) {
        if (tool == wanted && name != null && name.equalsIgnoreCase(held)) {
            clear();
            return;
        }
        this.tool = wanted;
        this.held = name;
    }

    // Same toggle rule as seed packets: picking up the tool you are already holding puts it down.
    public void selectTool(Tool wanted) {
        if (wanted == null || wanted == Tool.NONE || wanted == tool) {
            clear();
            return;
        }
        this.tool = wanted;
        this.held = null;
    }

    public void clear() {
        this.tool = Tool.NONE;
        this.held = null;
    }
}
