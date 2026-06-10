package models.entities.collectibles;

public class Sun extends Collectible{
    private SunType type;
    private int amount;
    private boolean falling;
    private double targetY;

    public void onReachGround(){};

    @Override
    public void update() {}
}
