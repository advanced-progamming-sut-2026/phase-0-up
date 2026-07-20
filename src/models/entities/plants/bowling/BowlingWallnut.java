package models.entities.plants.bowling;

// The standard bowling nut: rolls straight, damages a zombie on contact then turns 45 degrees, and
// turns 90 degrees off the top/bottom wall (handled by WallnutBowlingMode).
public class BowlingWallnut extends BowlingType {
    public BowlingWallnut(String name, int id, double x, int y) {
        super(name, id, x, y, BowlingKind.BOWLING);
    }
}
