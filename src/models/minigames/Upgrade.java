package models.minigames;

// One Beghouled plant upgrade: spend `cost` sun to turn every `fromPlant` on the board into a
// `toPlant`. Upgrades chain (e.g. Cabbage-pult -> Melon-pult -> Winter Melon) by making a plant that is
// itself the `fromPlant` of a further upgrade.
public class Upgrade {
    private final String fromPlant;
    private final String toPlant;
    private final int cost;

    public Upgrade(String fromPlant, String toPlant, int cost) {
        this.fromPlant = fromPlant;
        this.toPlant = toPlant;
        this.cost = cost;
    }

    public String getFromPlant() { return fromPlant; }
    public String getToPlant() { return toPlant; }
    public int getCost() { return cost; }
}
