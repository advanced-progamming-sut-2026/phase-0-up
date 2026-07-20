package models.entities.interactables;

// A special Vasebreaker vase that is guaranteed to release a Gargantuar (the "giant vase").
public class GargantuarVase extends Vase {
    public static final String GARGANTUAR_ALIAS = "ZombieGargantuar";

    public GargantuarVase(int x, int y) {
        super(x, y, VaseContent.ZOMBIE, GARGANTUAR_ALIAS);
    }
}
