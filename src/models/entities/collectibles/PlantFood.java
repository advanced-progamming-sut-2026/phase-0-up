package models.entities.collectibles;

import models.game.GameSession;

public class PlantFood extends Collectible{
    private int amount;

    private boolean falling;
    private double currentY;
    private double targetY;
    private double fallSpeed;
    public PlantFood(double x, double startY, double targetY, int amount, boolean falling, int expireTicks) {
        super("plantFood", x,(int) startY, expireTicks);
        this.amount = amount;
        this.falling = falling;
        this.fallSpeed = 0.05;
    }

    public void onReachGround() {
        this.falling = false;
        this.y = (int) currentY;
    }

    @Override
    protected void applyEffect(GameSession gameSession) {
        gameSession.increasePlantFoodCount(amount);
    }

    @Override
    public void update(GameSession gameSession) {
        super.update(gameSession);

        if (isRemovable()) {
            return;
        }

        if (falling) {
            if (currentY < targetY) {
                currentY += fallSpeed;
                this.y = (int) currentY;
            } else {
                onReachGround();
            }
        }
    }

    public int getAmount() { return amount; }
    public boolean isFalling() { return falling; }
}
