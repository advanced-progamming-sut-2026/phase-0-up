package models.entities.collectibles;

import models.game.GameSession;

public class Sun extends Collectible {
    private SunType type;
    private int amount;

    private boolean falling;
    private double currentY;
    private double targetY;
    private double fallSpeed;

    public Sun(double x, double startY, double targetY, SunType type, int amount, boolean falling, int expireTicks) {
        super(x, (int) startY, expireTicks);
        this.currentY = startY;
        this.targetY = targetY;
        this.type = type;
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
        gameSession.increaseSunAmount(this.amount);
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

    // --- Getters ---
    public SunType getType() { return type; }
    public int getAmount() { return amount; }
    public boolean isFalling() { return falling; }
}
