package models.entities.interactables;

// A special Vasebreaker vase that is guaranteed to hold a plant's seed packet (the "plant vase").
public class PlantVase extends Vase {
    public PlantVase(int x, int y, String plantName) {
        super(x, y, VaseContent.SEED_PACKET, plantName);
    }
}
