package models.entities.plants.bowling;

// The explosive nut: rolls straight and detonates a 3x3 Cherry-Bomb blast on the first zombie it meets
// (blast handled by WallnutBowlingMode).
public class ExplodeONut extends BowlingType {
    public ExplodeONut(String name, int id, double x, int y) {
        super(name, id, x, y, BowlingKind.EXPLODE);
    }
}
