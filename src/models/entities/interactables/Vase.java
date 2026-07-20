package models.entities.interactables;

// One clay vase on the Vasebreaker board. It hides one thing -- nothing, a zombie, or a one-use seed
// packet -- revealed only when the player breaks it. (x, y) is the grid cell it sits on (x = column,
// y = row). Subclasses PlantVase and GargantuarVase fix the content up front so a "special" vase is a
// type, not a flag.
public class Vase {
    private final int x;
    private final int y;
    private final VaseContent content;
    private final String payload;   // ZOMBIE -> zombie alias; SEED_PACKET -> plant name; EMPTY -> null
    private boolean broken;

    public Vase(int x, int y, VaseContent content, String payload) {
        this.x = x;
        this.y = y;
        this.content = content == null ? VaseContent.EMPTY : content;
        this.payload = payload;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public VaseContent getContent() { return content; }
    public String getPayload() { return payload; }
    public boolean isBroken() { return broken; }

    // Smashes the vase open once, exposing its content. Re-breaking an already-broken vase reports
    // EMPTY so a caller can never act on the same content twice; callers still guard on isBroken().
    public VaseContent breakOpen() {
        if (broken) {
            return VaseContent.EMPTY;
        }
        broken = true;
        return content;
    }
}
