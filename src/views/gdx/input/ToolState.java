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
        PLANT_FOOD
    }

    private Tool tool = Tool.NONE;
    private String seedName;

    public Tool tool() {
        return tool;
    }

    // The plant this seed packet will place, in the template's own display casing ("Snow Pea"), which
    // is what the plant command expects. Null unless a seed is armed.
    public String seedName() {
        return seedName;
    }

    public boolean isHolding() {
        return tool != Tool.NONE;
    }

    // Arms a seed packet. Selecting the packet that is already armed puts it back -- clicking a card
    // twice to cancel is how the original game behaves, and it saves needing a separate cancel target.
    public void selectSeed(String name) {
        if (tool == Tool.SEED && name != null && name.equalsIgnoreCase(seedName)) {
            clear();
            return;
        }
        this.tool = Tool.SEED;
        this.seedName = name;
    }

    // Same toggle rule as seed packets: picking up the tool you are already holding puts it down.
    public void selectTool(Tool wanted) {
        if (wanted == null || wanted == Tool.NONE || wanted == tool) {
            clear();
            return;
        }
        this.tool = wanted;
        this.seedName = null;
    }

    public void clear() {
        this.tool = Tool.NONE;
        this.seedName = null;
    }
}
