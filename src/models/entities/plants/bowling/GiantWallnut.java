package models.entities.plants.bowling;

// The giant nut: rolls perfectly straight, crushing (instantly killing) every zombie it rolls over and
// continuing forward without changing direction (handled by WallnutBowlingMode).
public class GiantWallnut extends BowlingType {
    public GiantWallnut(String name, int id, double x, int y) {
        super(name, id, x, y, BowlingKind.GIANT);
    }
}
