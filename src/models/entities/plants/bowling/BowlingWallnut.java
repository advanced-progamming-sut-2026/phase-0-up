package models.entities.plants.bowling;

public class BowlingWallnut extends BowlingType{
    private int direction;

    public BowlingWallnut(String name, int id, double x, int y) {
        super(name, id, x, y);
    }

    public void roll(){};
    public void bounce(){};

}
